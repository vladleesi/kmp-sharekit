#import <AppKit/AppKit.h>
#include <jni.h>

// All session state is confined to the AppKit main thread.
@interface SKShareSession : NSObject <NSSharingServicePickerDelegate>
@property(nonatomic, strong) NSSharingServicePicker *picker;
@property(nonatomic, strong) NSWindow *window;
@property(nonatomic, copy) NSString *title;
@property(nonatomic, copy) void (^completion)(jint, NSString *);
@property(nonatomic, strong) id closeObserver;
@property(nonatomic) BOOL finished;
- (void)finish:(jint)status message:(NSString *)message;
@end

static SKShareSession *activeSession;

@implementation SKShareSession
- (void)finish:(jint)status message:(NSString *)message {
    if (self.finished) return;
    self.finished = YES;
    if (self.closeObserver) {
        [NSNotificationCenter.defaultCenter removeObserver:self.closeObserver];
        self.closeObserver = nil;
    }
    self.picker.delegate = nil;
    void (^completion)(jint, NSString *) = self.completion;
    self.completion = nil;
    // Keep the picker/host alive until the current AppKit delegate stack unwinds.
    SKShareSession *retained = self;
    if (activeSession == self) activeSession = nil;
    dispatch_async(dispatch_get_main_queue(), ^{ (void)retained; });
    if (completion) completion(status, message);
}

- (id<NSSharingServiceDelegate>)sharingServicePicker:(NSSharingServicePicker *)picker
                         delegateForSharingService:(NSSharingService *)service {
    service.subject = self.title;
    return nil;
}

- (void)sharingServicePicker:(NSSharingServicePicker *)picker
    didChooseSharingService:(NSSharingService *)service {
    // Handoff, not delivery: many extensions cannot report their eventual outcome reliably.
    if (service) service.subject = self.title;
    [self finish:service ? 0 : 1 message:nil];
}
@end

static void presentItemsWithPicker(NSArray *items, NSString *title, void (^completion)(jint, NSString *),
                                   NSSharingServicePicker *(^createPicker)(NSArray *)) {
    NSCAssert(NSThread.isMainThread, @"Sharing must run on the AppKit main thread");
    if (activeSession) {
        completion(3, nil);
        return;
    }
    NSWindow *window = NSApp.keyWindow;
    NSView *view = window.contentView;
    if (!window.isVisible || window.isMiniaturized || !view || NSIsEmptyRect(view.visibleRect)) {
        completion(2, nil);
        return;
    }
    SKShareSession *session = [SKShareSession new];
    session.window = window;
    session.title = title;
    session.completion = completion;
    activeSession = session;
    @try {
        session.picker = createPicker(items);
        session.picker.delegate = session;
        __weak SKShareSession *weakSession = session;
        session.closeObserver = [NSNotificationCenter.defaultCenter
            addObserverForName:NSWindowWillCloseNotification object:window queue:nil
            usingBlock:^(NSNotification *notification) {
                SKShareSession *owner = weakSession;
                if (!owner || owner.finished) return;
                [owner finish:1 message:nil];
                [owner.picker close];
            }];
        // Anchor within the active window, including keyboard-triggered requests.
        NSRect bounds = view.visibleRect;
        NSPoint point = [view convertPoint:window.mouseLocationOutsideOfEventStream fromView:nil];
        if (!NSPointInRect(point, bounds)) point = NSMakePoint(NSMidX(bounds), NSMidY(bounds));
        [session.picker showRelativeToRect:NSMakeRect(point.x, point.y, 1, 1)
                                  ofView:view preferredEdge:NSMinYEdge];
    } @catch (NSException *error) {
        [session finish:4 message:[NSString stringWithFormat:@"%@: %@", error.name, error.reason]];
        @try { [session.picker close]; } @catch (NSException *closeError) { /* Preserve the original failure. */ }
    }
}

// JNI uses UTF-16 rather than modified UTF-8 to preserve emoji and embedded NUL characters.
static NSString *fromJava(JNIEnv *env, jstring value) {
    if (!value) return nil;
    const jchar *chars = (*env)->GetStringChars(env, value, NULL);
    if (!chars) return nil;
    NSString *result = [[NSString alloc] initWithCharacters:chars length:(*env)->GetStringLength(env, value)];
    (*env)->ReleaseStringChars(env, value, chars);
    return result;
}

JNIEXPORT jboolean JNICALL Java_dev_vladleesi_kmpsharekit_MacSharingBridge_available(JNIEnv *env, jobject bridge) {
    if (@available(macOS 13.0, *)) return JNI_TRUE;
    return JNI_FALSE;
}

JNIEXPORT void JNICALL Java_dev_vladleesi_kmpsharekit_MacSharingBridge_present(
    JNIEnv *env, jobject bridge, jstring text, jstring url, jstring title, jobject callback) {
    @autoreleasepool {
        NSString *nativeText = fromJava(env, text);
        NSString *nativeUrl = fromJava(env, url);
        NSString *nativeTitle = fromJava(env, title);
        if ((*env)->ExceptionCheck(env)) return;
        jclass callbackClass = (*env)->GetObjectClass(env, callback);
        if (!callbackClass) return;
        jmethodID complete = (*env)->GetMethodID(env, callbackClass, "complete", "(ILjava/lang/String;)V");
        (*env)->DeleteLocalRef(env, callbackClass);
        if (!complete) return;
        jobject retainedCallback = (*env)->NewGlobalRef(env, callback);
        if (!retainedCallback) return;
        JavaVM *vm = NULL;
        (*env)->GetJavaVM(env, &vm);
        dispatch_async(dispatch_get_main_queue(), ^{
            @autoreleasepool {
                void (^completion)(jint, NSString *) = ^(jint status, NSString *message) {
                    JNIEnv *callbackEnv = NULL;
                    BOOL attached = (*vm)->GetEnv(vm, (void **)&callbackEnv, JNI_VERSION_1_6) == JNI_EDETACHED;
                    if (attached && (*vm)->AttachCurrentThreadAsDaemon(vm, (void **)&callbackEnv, NULL) != JNI_OK) return;
                    jstring detail = NULL;
                    if (message) {
                        NSUInteger length = message.length;
                        unichar *buffer = calloc(length + 1, sizeof(unichar));
                        if (buffer) {
                            [message getCharacters:buffer range:NSMakeRange(0, length)];
                            detail = (*callbackEnv)->NewString(callbackEnv, buffer, (jsize)length);
                            free(buffer);
                        }
                    }
                    if (!(*callbackEnv)->ExceptionCheck(callbackEnv)) {
                        (*callbackEnv)->CallVoidMethod(callbackEnv, retainedCallback, complete, status, detail);
                    }
                    // The adapter only queues work onto AWT; user exceptions never cross JNI.
                    if ((*callbackEnv)->ExceptionCheck(callbackEnv)) {
                        (*callbackEnv)->ExceptionDescribe(callbackEnv);
                        (*callbackEnv)->ExceptionClear(callbackEnv);
                    }
                    if (detail) (*callbackEnv)->DeleteLocalRef(callbackEnv, detail);
                    (*callbackEnv)->DeleteGlobalRef(callbackEnv, retainedCallback);
                    if (attached) (*vm)->DetachCurrentThread(vm);
                };
                NSMutableArray *items = [NSMutableArray array];
                if (nativeText) [items addObject:nativeText];
                if (nativeUrl) {
                    NSURL *parsed = [NSURL URLWithString:nativeUrl];
                    if (!parsed) { completion(4, @"Unable to create native URL"); return; }
                    [items addObject:parsed];
                }
                presentItemsWithPicker(items, nativeTitle, completion, ^NSSharingServicePicker *(NSArray *payload) {
                    return [[NSSharingServicePicker alloc] initWithItems:payload];
                });
            }
        });
    }
}
