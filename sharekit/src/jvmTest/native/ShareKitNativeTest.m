// Tests the production delegate/ownership code without opening sharing UI or invoking a recipient.
#import "../../jvmMain/native/ShareKit.m"

@interface TestView : NSObject
@end
@implementation TestView
- (NSRect)visibleRect { return NSMakeRect(0, 0, 400, 300); }
- (NSPoint)convertPoint:(NSPoint)point fromView:(NSView *)view { return point; }
@end

@interface TestWindow : NSObject
@property BOOL visible;
@property BOOL miniaturized;
@property(strong) TestView *contentView;
@end
@implementation TestWindow
- (BOOL)isVisible { return self.visible; }
- (BOOL)isMiniaturized { return self.miniaturized; }
- (NSPoint)mouseLocationOutsideOfEventStream { return NSMakePoint(-100, -100); }
@end

@interface TestApp : NSObject
@property(strong) TestWindow *keyWindow;
@end
@implementation TestApp
@end

@interface TestPicker : NSObject
@property(weak) id<NSSharingServicePickerDelegate> delegate;
@property BOOL presented;
@property BOOL closed;
@property BOOL failPresentation;
@end
@implementation TestPicker
- (void)showRelativeToRect:(NSRect)rect ofView:(NSView *)view preferredEdge:(NSRectEdge)edge {
    NSCAssert(NSEqualRects(rect, NSMakeRect(200, 150, 1, 1)), @"Keyboard anchor must stay inside the host");
    if (self.failPresentation) [NSException raise:@"TestFailure" format:@"Presentation failed"];
    self.presented = YES;
}
- (void)close { self.closed = YES; }
@end

int main(void) {
    @autoreleasepool {
        TestApp *app = [TestApp new];
        NSApp = (NSApplication *)app;
        __block NSInteger calls = 0;
        __block jint outcome = -1;
        __block NSString *detail;
        void (^complete)(jint, NSString *) = ^(jint status, NSString *message) {
            calls++;
            outcome = status;
            detail = message;
        };
        __block TestPicker *picker;
        NSSharingServicePicker *(^factory)(NSArray *) = ^NSSharingServicePicker *(NSArray *items) {
            NSCAssert(([items isEqual:@[@"Hello 🌍", [NSURL URLWithString:@"https://example.com"]]]), @"Payload changed");
            return (NSSharingServicePicker *)picker;
        };
        NSArray *items = @[@"Hello 🌍", [NSURL URLWithString:@"https://example.com"]];
        presentItemsWithPicker(items, @"Title", complete, factory);
        NSCAssert(calls == 1 && outcome == 2 && !activeSession, @"Missing host must release callback");

        TestWindow *window = [TestWindow new];
        window.visible = YES;
        window.contentView = [TestView new];
        app.keyWindow = window;
        for (NSInteger mode = 0; mode < 4; mode++) {
            calls = 0;
            picker = [TestPicker new];
            picker.failPresentation = mode == 3;
            presentItemsWithPicker(items, @"Title", complete, factory);
            if (mode == 3) {
                NSCAssert(calls == 1 && outcome == 4 && [detail containsString:@"TestFailure"] && !activeSession && picker.closed,
                          @"Presentation exception must complete and clean up");
                continue;
            }
            NSCAssert(picker.presented && activeSession && activeSession.window == (NSWindow *)window,
                      @"Session must own the presentation host");
            presentItemsWithPicker(items, @"Title", ^(jint status, NSString *message) {
                NSCAssert(status == 3, @"Overlapping native requests must be busy");
            }, factory);
            SKShareSession *session = activeSession;
            if (mode == 0) {
                [session sharingServicePicker:(NSSharingServicePicker *)picker didChooseSharingService:nil];
                NSCAssert(outcome == 1, @"Picker dismissal must report cancellation");
            } else if (mode == 1) {
                NSSharingService *service = [[NSSharingService alloc] initWithTitle:@"Test" image:[NSImage new]
                                                                   alternateImage:nil handler:^{}];
                [session sharingServicePicker:(NSSharingServicePicker *)picker didChooseSharingService:service];
                NSCAssert(outcome == 0 && [service.subject isEqual:@"Title"], @"Selection must set subject and report handoff");
            } else {
                [NSNotificationCenter.defaultCenter postNotificationName:NSWindowWillCloseNotification object:window];
                NSCAssert(outcome == 1 && picker.closed, @"Closing the host must close its picker");
            }
            [session finish:4 message:@"Late duplicate"];
            NSCAssert(calls == 1 && !activeSession && !session.completion && !session.closeObserver && !picker.delegate,
                      @"Terminal paths must release callback/observer/delegate exactly once");
        }
        window.miniaturized = YES;
        calls = 0;
        presentItemsWithPicker(items, nil, complete, factory);
        NSCAssert(calls == 1 && outcome == 2, @"Minimized hosts must be rejected");
        // Run deferred releases and check that a finished session does not retain its host.
        __weak TestWindow *releasedWindow = window;
        app.keyWindow = nil;
        window = nil;
        __block BOOL drained = NO;
        dispatch_async(dispatch_get_main_queue(), ^{ drained = YES; });
        while (!drained) [NSRunLoop.currentRunLoop runUntilDate:[NSDate dateWithTimeIntervalSinceNow:0.01]];
        NSCAssert(!releasedWindow, @"Finished sessions must release the host");
        NSApp = nil;
        puts("Native macOS sharing tests passed.");
    }
    return 0;
}
