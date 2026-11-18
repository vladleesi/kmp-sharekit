#include "../../../jvmMain/native/windows/ShareKit.cpp"
#include <filesystem>
#include <fstream>
#include <future>
#include <iostream>
#include <thread>

static void require(bool condition, char const* message) {
    if (!condition) throw std::runtime_error(message);
}

struct Attachment {
    wchar_t path[MAX_PATH]{};

    explicit Attachment(char const* contents) {
        wchar_t directory[MAX_PATH];
        DWORD length = GetTempPathW(MAX_PATH, directory);
        require(length > 0 && length < MAX_PATH, "Missing temporary directory");
        require(GetTempFileNameW(directory, L"sk", 0, path) != 0, "Unable to create an attachment fixture");
        std::ofstream file(std::filesystem::path(path), std::ios::binary);
        file << contents;
        file.close();
        if (!file) {
            DeleteFileW(path);
            throw std::runtime_error("Unable to write an attachment fixture");
        }
    }

    ~Attachment() { DeleteFileW(path); }

    Attachment(Attachment const&) = delete;
    Attachment& operator=(Attachment const&) = delete;
};

int main() {
    try {
        using namespace sharekit;
        Apartment apartment(RO_INIT_MULTITHREADED);
        winrt::check_hresult(apartment.result);
        // Exercise the real WinRT data package, including UTF-16 and URL metadata.
        DataPackage package;
        Payload{L"Hello \U0001F30D\nSecond line", L"https://example.com/a?q=1", L"A title"}.populate(package);
        auto view = package.GetView();
        require(view.GetTextAsync().get() == L"Hello \U0001F30D\nSecond line", "Unicode text changed");
        require(view.GetWebLinkAsync().get().AbsoluteUri() == L"https://example.com/a?q=1", "URL changed");
        require(view.Properties().Title() == L"A title", "Title changed");
        DataPackage fallback;
        Payload{L"Text", {}, {}}.populate(fallback);
        require(fallback.GetView().Properties().Title() == L"Share", "Missing default title");
        Attachment first("First attachment");
        Attachment second("Second attachment");
        auto firstName = std::filesystem::path(first.path).filename().wstring();
        auto secondName = std::filesystem::path(second.path).filename().wstring();
        DataPackage attachments;
        addFiles(attachments, {first.path, second.path}).get();
        auto items = attachments.GetView().GetStorageItemsAsync().get();
        require(items.Size() == 2, "Storage items missing");
        bool receivedFirst = false;
        bool receivedSecond = false;
        for (auto const& item : items) {
            auto file = item.as<StorageFile>();
            auto name = file.Name();
            auto contents = FileIO::ReadTextAsync(file).get();
            receivedFirst |= name == firstName && contents == L"First attachment";
            receivedSecond |= name == secondName && contents == L"Second attachment";
        }
        require(receivedFirst && receivedSecond, "Share data did not preserve both attachment names and contents");
        require(!usableHost(nullptr), "Null host accepted");

        std::promise<HWND> ready;
        auto future = ready.get_future();
        std::thread owner([&ready] {
            Apartment apartment;
            HWND window = CreateWindowExW(0, L"STATIC", L"ShareKit native test", WS_OVERLAPPEDWINDOW,
                                           0, 0, 240, 160, nullptr, nullptr, GetModuleHandleW(nullptr), nullptr);
            ready.set_value(window);
            MSG message;
            while (GetMessageW(&message, nullptr, 0, 0) > 0) {
                TranslateMessage(&message);
                DispatchMessageW(&message);
            }
        });
        HWND window = future.get();
        bool valid = window != nullptr;
        HRESULT dispatched = E_FAIL;
        HRESULT failed = E_FAIL;
        bool cleanup = false;
        bool panelSupported = false;
        bool panelLaunched = false;
        if (window) {
            dispatched = onWindowThread(window, [&] {
                DWORD thread = GetWindowThreadProcessId(window, nullptr);
                require(thread == GetCurrentThreadId(), "Not on the HWND owner thread");
                require(!usableHost(window), "Hidden host accepted");
                auto session = std::make_unique<Session>();
                require(SUCCEEDED(session->apartment.result), "Owner thread is not STA");
                winrt::check_bool(SetWindowSubclass(window, windowProcedure, subclassId,
                                                   reinterpret_cast<DWORD_PTR>(session.get())));
                session.release();
                clearSession(window);
                DWORD_PTR reference = 0;
                require(!GetWindowSubclass(window, windowProcedure, subclassId, &reference), "Registration leaked");
                auto closingSession = std::make_unique<Session>();
                winrt::check_bool(SetWindowSubclass(window, windowProcedure, subclassId,
                                                   reinterpret_cast<DWORD_PTR>(closingSession.get())));
                closingSession.release();
                panelSupported = DataTransferManager::IsSupported();
                if (panelSupported) {
                    ShowWindow(window, SW_SHOW);
                    SetForegroundWindow(window);
                    presentForWindow(window, Payload{L"ShareKit test \U0001F30D", L"https://example.com", L"Native smoke"});
                    panelLaunched = true;
                }
            });
            failed = onWindowThread(window, [] { winrt::throw_hresult(E_ACCESSDENIED); });
            onWindowThread(window, [&] {
                DestroyWindow(window); // WM_NCDESTROY releases the remaining session.
                cleanup = !IsWindow(window);
                PostQuitMessage(0);
            });
        } else {
            // The owner thread must also terminate if creating a window failed.
            PostThreadMessageW(GetThreadId(owner.native_handle()), WM_QUIT, 0, 0);
        }
        owner.join();
        require(valid, "Unable to create test window");
        require(dispatched == S_OK, "Native owner-thread dispatch failed");
        require(failed == E_ACCESSDENIED, "Native error was lost");
        require(cleanup, "Host was not destroyed");
        require(!panelSupported || panelLaunched, "Supported Windows share panel did not launch");
        require(onWindowThread(nullptr, [] {}) == HRESULT_FROM_WIN32(ERROR_INVALID_WINDOW_HANDLE), "Invalid host dispatched");
        std::cout << "Windows payload, owner-thread dispatch, HRESULT, and host lifecycle checks passed.\n";
        std::cout << (panelLaunched ? "Real Windows share panel launch passed.\n" : "Panel launch skipped: this Windows session does not support sharing.\n");
        return 0;
    } catch (winrt::hresult_error const& error) {
        std::wcerr << error.message().c_str() << L"\n";
    } catch (std::exception const& error) {
        std::cerr << error.what() << "\n";
    }
    return 1;
}
