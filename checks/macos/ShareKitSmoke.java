import dev.vladleesi.kmpsharekit.ShareKit;
import dev.vladleesi.kmpsharekit.ShareMethod;
import dev.vladleesi.kmpsharekit.ShareRequest;
import dev.vladleesi.kmpsharekit.ShareResult;
import java.awt.EventQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import kotlin.Unit;

/** Small packaged-app check; --smoke closes the host without selecting a recipient. */
public final class ShareKitSmoke {
    public static void main(String[] args) {
        boolean smoke = args.length > 0 && args[0].equals("--smoke");
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            error.printStackTrace();
            System.exit(1);
        });
        EventQueue.invokeLater(() -> {
            if (ShareKit.INSTANCE.capabilities().getMethod() != ShareMethod.NATIVE) {
                throw new AssertionError("Packaged native library unavailable");
            }
            JFrame window = new JFrame("ShareKit macOS check");
            window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            JLabel status = new JLabel("Share text, URL, and title from this window");
            JButton nativeButton = new JButton("Native share");
            JButton emailButton = new JButton("Email");
            JPanel content = new JPanel();
            content.add(status);
            content.add(nativeButton);
            content.add(emailButton);
            window.setContentPane(content);
            window.setSize(560, 160);
            window.setLocationRelativeTo(null);
            ShareRequest request = new ShareRequest("Hello 🌍\nПривет 世界", "https://example.com", "ShareKit check");
            AtomicBoolean closeRequested = new AtomicBoolean();
            AtomicBoolean completed = new AtomicBoolean();
            nativeButton.addActionListener(event -> ShareKit.INSTANCE.share(request, result -> {
                if (!EventQueue.isDispatchThread()) throw new AssertionError("Callback must run on AWT");
                status.setText(result.toString());
                if (smoke) {
                    if (!closeRequested.get() || result != ShareResult.Cancelled.INSTANCE) {
                        throw new AssertionError("Expected cancellation after host closure, got " + result);
                    }
                    if (!completed.compareAndSet(false, true)) throw new AssertionError("Duplicate callback");
                    ShareKit.INSTANCE.share(request, missingHost -> {
                        if (!(missingHost instanceof ShareResult.Failed)
                                || ((ShareResult.Failed) missingHost).getReason()
                                    != dev.vladleesi.kmpsharekit.ShareFailure.HOST_UNAVAILABLE) {
                            throw new AssertionError("Expected missing host, got " + missingHost);
                        }
                        System.out.println("Packaged macOS JNI smoke passed: native picker, host closure, AWT callback, missing host.");
                        System.exit(0);
                        return Unit.INSTANCE;
                    });
                }
                return Unit.INSTANCE;
            }));
            emailButton.addActionListener(event -> ShareKit.INSTANCE.share(request, ShareMethod.EMAIL, result -> {
                status.setText(result.toString());
                return Unit.INSTANCE;
            }));
            window.setVisible(true);
            window.toFront();
            if (smoke) {
                Timer start = new Timer(1500, event -> nativeButton.doClick());
                start.setRepeats(false);
                start.start();
                Timer close = new Timer(4500, event -> {
                    closeRequested.set(true);
                    window.dispose();
                });
                close.setRepeats(false);
                close.start();
                Timer timeout = new Timer(15000, event -> { throw new AssertionError("Native callback timed out"); });
                timeout.setRepeats(false);
                timeout.start();
            }
        });
    }
}
