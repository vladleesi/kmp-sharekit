#include <windows.h>
#include <roapi.h>
#include <commctrl.h>
#include <shobjidl.h>
#include <winrt/Windows.ApplicationModel.DataTransfer.h>
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Foundation.Collections.h>
#include <winrt/Windows.Storage.h>
#include <jni.h>
#include <functional>
#include <cstdio>
#include <memory>
#include <string>
#include <vector>

namespace sharekit {
using namespace winrt::Windows::ApplicationModel::DataTransfer;
using namespace winrt::Windows::Storage;

// The HWND, WinRT manager, event registration, and subclass all belong to the
// native window thread (AWT's Windows toolkit thread, distinct from Java's EDT).
constexpr UINT_PTR subclassId = 0x534B;
const UINT dispatchMessage = RegisterWindowMessageW(L"ShareKit.Windows.Dispatch.v1");

struct Apartment {
    HRESULT result;
    explicit Apartment(RO_INIT_TYPE kind = RO_INIT_SINGLETHREADED) : result(RoInitialize(kind)) { }
    ~Apartment() { if (SUCCEEDED(result)) RoUninitialize(); }
};

struct Payload {
    std::wstring text;
    std::wstring url;
    std::wstring title;
    std::vector<std::wstring> paths{};

    void populate(DataPackage const& data) const {
        data.Properties().Title(title.empty() ? L"Share" : winrt::hstring(title));
        data.RequestedOperation(DataPackageOperation::Copy);
        if (!text.empty()) data.SetText(text);
        if (!url.empty()) data.SetWebLink(winrt::Windows::Foundation::Uri(url));
    }
};

static void reportDataFailure(DataRequest const& request) noexcept {
    // Panel launch was already reported. Show data preparation failures in the system UI.
    try { request.FailWithDisplayText(L"Unable to prepare content for sharing."); } catch (...) { }
}

static winrt::Windows::Foundation::IAsyncAction addFiles(DataPackage data, std::vector<std::wstring> paths) {
    std::vector<IStorageItem> files;
    files.reserve(paths.size());
    for (auto const& path : paths) {
        auto file = co_await StorageFile::GetFileFromPathAsync(path);
        files.push_back(file.as<IStorageItem>());
    }
    data.SetStorageItems(winrt::single_threaded_vector<IStorageItem>(std::move(files)), true);
}

static winrt::fire_and_forget populateFiles(DataRequest request, DataRequestDeferral deferral,
                                            std::vector<std::wstring> paths) {
    try {
        co_await addFiles(request.Data(), std::move(paths));
    } catch (...) {
        reportDataFailure(request);
    }
    try { deferral.Complete(); } catch (...) { } // The panel may have closed before the files are ready.
}

struct Session {
    DataTransferManager manager{nullptr};
    winrt::event_token requested{};
    bool registered = false;
    // Keep the apartment initialized for the lifetime of its WinRT objects.
    Apartment apartment;

    ~Session() {
        if (registered) {
            try { manager.DataRequested(requested); } catch (...) { }
        }
        manager = nullptr;
    }
};

static LRESULT CALLBACK windowProcedure(HWND window, UINT message, WPARAM wparam,
                                        LPARAM lparam, UINT_PTR id, DWORD_PTR reference) {
    if (message == WM_NCDESTROY) {
        RemoveWindowSubclass(window, windowProcedure, id);
        delete reinterpret_cast<Session*>(reference);
    }
    return DefSubclassProc(window, message, wparam, lparam);
}

static void clearSession(HWND window) {
    DWORD_PTR reference = 0;
    if (GetWindowSubclass(window, windowProcedure, subclassId, &reference)) {
        RemoveWindowSubclass(window, windowProcedure, subclassId);
        delete reinterpret_cast<Session*>(reference);
    }
}

static bool usableHost(HWND window) {
    DWORD process = 0;
    GetWindowThreadProcessId(window, &process);
    return window && process == GetCurrentProcessId() && IsWindowVisible(window) && !IsIconic(window);
}

static void presentForWindow(HWND window, Payload const& payload) {
    clearSession(window);
    auto session = std::make_unique<Session>();
    winrt::check_hresult(session->apartment.result);
    auto interop = winrt::get_activation_factory<DataTransferManager, IDataTransferManagerInterop>();
    winrt::check_hresult(interop->GetForWindow(window, winrt::guid_of<DataTransferManager>(),
                                             winrt::put_abi(session->manager)));
    session->requested = session->manager.DataRequested([payload](auto const&, DataRequestedEventArgs const& args) {
        auto request = args.Request();
        try {
            payload.populate(request.Data());
            if (!payload.paths.empty()) {
                auto paths = payload.paths;
                populateFiles(request, request.GetDeferral(), std::move(paths));
            }
        } catch (...) {
            reportDataFailure(request);
        }
    });
    session->registered = true;
    winrt::check_bool(SetWindowSubclass(window, windowProcedure, subclassId,
                                       reinterpret_cast<DWORD_PTR>(session.get())));
    session.release(); // The subclass owns the registration until replacement or host destruction.
    try {
        winrt::check_hresult(interop->ShowShareUIForWindow(window));
    } catch (...) {
        clearSession(window);
        throw;
    }
}

struct Dispatch {
    std::function<void()> action;
    HRESULT result = E_FAIL;
    bool handled = false;
};

static LRESULT CALLBACK dispatchHook(int code, WPARAM wparam, LPARAM lparam) {
    if (code >= 0) {
        auto message = reinterpret_cast<CWPSTRUCT*>(lparam);
        if (message->message == dispatchMessage) {
            auto dispatch = reinterpret_cast<Dispatch*>(message->lParam);
            if (!dispatch->handled) {
                dispatch->handled = true;
                try {
                    dispatch->action();
                    dispatch->result = S_OK;
                } catch (...) {
                    dispatch->result = winrt::to_hresult();
                }
            }
        }
    }
    return CallNextHookEx(nullptr, code, wparam, lparam);
}

// SendMessage keeps the request alive until the owner thread finishes. No Java
// objects or callbacks cross into the hook or survive the JNI invocation.
static HRESULT onWindowThread(HWND window, std::function<void()> action) {
    DWORD process = 0;
    DWORD thread = GetWindowThreadProcessId(window, &process);
    if (!thread || process != GetCurrentProcessId()) return HRESULT_FROM_WIN32(ERROR_INVALID_WINDOW_HANDLE);
    Dispatch dispatch{std::move(action)};
    if (thread == GetCurrentThreadId()) {
        try { dispatch.action(); return S_OK; } catch (...) { return winrt::to_hresult(); }
    }
    HHOOK hook = SetWindowsHookExW(WH_CALLWNDPROC, dispatchHook, nullptr, thread);
    if (!hook) return HRESULT_FROM_WIN32(GetLastError());
    SendMessageW(window, dispatchMessage, 0, reinterpret_cast<LPARAM>(&dispatch));
    UnhookWindowsHookEx(hook);
    return dispatch.result;
}

static std::wstring fromJava(JNIEnv* env, jstring value) {
    if (!value || env->ExceptionCheck()) return {};
    auto chars = env->GetStringChars(value, nullptr);
    if (!chars) return {};
    std::wstring result(reinterpret_cast<wchar_t const*>(chars), env->GetStringLength(value));
    env->ReleaseStringChars(value, chars);
    return result;
}
} // namespace sharekit

extern "C" JNIEXPORT jboolean JNICALL Java_dev_vladleesi_kmpsharekit_WindowsSharingBridge_available(
    JNIEnv*, jobject) {
    sharekit::Apartment apartment;
    // A static capability query can also run on an already initialized MTA.
    if (FAILED(apartment.result) && apartment.result != RPC_E_CHANGED_MODE) return JNI_FALSE;
    try { return sharekit::DataTransferManager::IsSupported() ? JNI_TRUE : JNI_FALSE; }
    catch (...) { return JNI_FALSE; }
}

static jint presentPayload(JNIEnv* env, jstring text, jstring url, jstring title, jobjectArray paths) {
    try {
        sharekit::Payload payload{sharekit::fromJava(env, text), sharekit::fromJava(env, url), sharekit::fromJava(env, title)};
        if (paths && !env->ExceptionCheck()) {
            jsize count = env->GetArrayLength(paths);
            payload.paths.reserve(count);
            for (jsize index = 0; index < count; index++) {
                auto path = static_cast<jstring>(env->GetObjectArrayElement(paths, index));
                if (!path) return 4;
                auto nativePath = sharekit::fromJava(env, path);
                env->DeleteLocalRef(path);
                if (env->ExceptionCheck()) return 4;
                payload.paths.push_back(std::move(nativePath));
            }
        }
        if (env->ExceptionCheck()) return 4;
        HWND window = GetForegroundWindow();
        if (!sharekit::usableHost(window)) return 2;
        HRESULT result = sharekit::onWindowThread(window, [window, &payload] {
            if (!sharekit::usableHost(window)) winrt::throw_hresult(HRESULT_FROM_WIN32(ERROR_INVALID_WINDOW_HANDLE));
            sharekit::presentForWindow(window, payload);
        });
        if (result == E_ACCESSDENIED) return 5;
        if (result == HRESULT_FROM_WIN32(ERROR_INVALID_WINDOW_HANDLE)) return 2;
        if (result == REGDB_E_CLASSNOTREG || result == E_NOINTERFACE) return 6;
        winrt::check_hresult(result);
        return 0; // Panel launch only; selecting a target, cancellation, and delivery are not observed.
    } catch (...) {
        auto result = winrt::to_hresult();
        char message[96];
        sprintf_s(message, "Windows sharing failed (HRESULT 0x%08lX)", static_cast<unsigned long>(result));
        auto exception = env->FindClass("java/lang/IllegalStateException");
        if (exception) env->ThrowNew(exception, message);
        return 4;
    }
}

extern "C" JNIEXPORT jint JNICALL Java_dev_vladleesi_kmpsharekit_WindowsSharingBridge_present(
    JNIEnv* env, jobject, jstring text, jstring url, jstring title) {
    return presentPayload(env, text, url, title, nullptr);
}

extern "C" JNIEXPORT jint JNICALL Java_dev_vladleesi_kmpsharekit_WindowsSharingBridge_presentFiles(
    JNIEnv* env, jobject, jstring text, jstring title, jobjectArray paths) {
    return presentPayload(env, text, nullptr, title, paths);
}
