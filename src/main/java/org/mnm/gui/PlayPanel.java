package org.mnm.gui;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.mnm.client.RunnerOptions;
import org.mnm.config.Environment;
import org.mnm.config.SettingsStore;
import org.mnm.config.VersionDetails;
import org.mnm.events.ClientEventHandler;
import org.mnm.events.LoginListener;
import org.mnm.events.Refreshable;
import org.mnm.events.RepairListener;
import org.mnm.events.RunningClientsListener;

import static org.mnm.config.Client.Status.UPDATED;
import static org.mnm.config.SettingsStore.CLOSE_ON_PLAY_KEY;
import static org.mnm.config.SettingsStore.PLAY_ON_START_KEY;
import static org.mnm.gui.GuiComponents.setFontSize;
import static org.mnm.gui.Style.ACTION_BUTTON_FONT_SIZE;
import static org.mnm.gui.Style.BASE_FONT_SIZE;
import static org.mnm.gui.Style.SCALE;

public class PlayPanel extends JPanel
    implements LoginListener, RepairListener, Refreshable, RunningClientsListener {

    final JButton play;
    final JCheckBox closeOnPlay;
    final JCheckBox playOnStart;
    final VersionPanel versionPanel;

    private static final String PLAY_TEXT = "Play";
    // Time to cancel "Play on start" before the client starts
    static final int PLAY_ON_START_SECONDS = 3;

    // The client starts on a separate thread, so that the UI does not freeze while the client starts
    private static final Executor CLIENT_EXECUTOR = task -> Thread.ofPlatform().name("client-runner").daemon(false).start(task);

    private final Executor executor;
    private final Consumer<Process> closeOnLaunchAction;
    private final RunningClients runningClients = new RunningClients(ClientEventHandler.getInstance());
    private String currentSlug = null;
    private boolean canPlay = false;
    private boolean running = false;
    private final Timer countdown;
    private int remainingSeconds;
    private boolean counting = false;

    // Test-only constructor. Do not call it from production code.
    PlayPanel(
        GuiCommand.PlayAction runAction,
        Supplier<RunnerOptions> optionsSupplier,
        SettingsStore settingsStore,
        Consumer<Process> closeOnLaunchAction) {
        this(runAction, optionsSupplier, settingsStore, closeOnLaunchAction, 1f);
    }

    public PlayPanel(
        GuiCommand.PlayAction runAction,
        Supplier<RunnerOptions> optionsSupplier,
        SettingsStore settingsStore,
        Consumer<Process> closeOnLaunchAction,
        float uiScaling) {
        this(runAction, optionsSupplier, settingsStore, closeOnLaunchAction, uiScaling, CLIENT_EXECUTOR);
    }

    // Test-only constructor. Do not call it from production code.
    PlayPanel(
        GuiCommand.PlayAction runAction,
        Supplier<RunnerOptions> optionsSupplier,
        SettingsStore settingsStore,
        Consumer<Process> closeOnLaunchAction,
        float uiScaling,
        Executor executor) {

        this.executor = executor;
        this.closeOnLaunchAction = closeOnLaunchAction;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(2 * SCALE, 0, 0, 0));

        play = createButton(PLAY_TEXT, uiScaling);
        closeOnPlay = new CheckboxOption("Close Sweet on play", settingsStore, CLOSE_ON_PLAY_KEY, false);
        closeOnPlay.setEnabled(false);
        setFontSize(closeOnPlay, BASE_FONT_SIZE * uiScaling);
        // The checkbox is only available when Play is available
        play.addPropertyChangeListener("enabled", _ -> closeOnPlay.setEnabled(play.isEnabled()));
        play.addActionListener(_ -> {
            stopCountdown();
            play(runAction, optionsSupplier.get(), closeOnPlay.isSelected());
        });

        playOnStart = new CheckboxOption("Play on start", settingsStore, PLAY_ON_START_KEY, false);
        setFontSize(playOnStart, BASE_FONT_SIZE * uiScaling);
        // To clear the checkbox during the countdown cancels the start
        playOnStart.addActionListener(_ -> {
            if (!playOnStart.isSelected()) {
                stopCountdown();
            }
        });
        countdown = new Timer(1000, _ -> tick());

        JPanel playOptions = new JPanel();
        playOptions.setLayout(new BoxLayout(playOptions, BoxLayout.Y_AXIS));
        closeOnPlay.setAlignmentX(Component.LEFT_ALIGNMENT);
        playOnStart.setAlignmentX(Component.LEFT_ALIGNMENT);
        playOptions.add(closeOnPlay);
        playOptions.add(playOnStart);

        JPanel playRow = new JPanel(new FlowLayout(FlowLayout.CENTER, SCALE, 0));
        playRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        playRow.add(play);
        playRow.add(playOptions);
        this.add(playRow);
        this.versionPanel = new VersionPanel(uiScaling);
        this.add(versionPanel);

        ClientEventHandler.getInstance().register(this);
    }

    // Starts the client in the background, so that the UI does not freeze while the client starts
    private void play(GuiCommand.PlayAction runAction, RunnerOptions options, boolean closeOnLaunch) {
        running = true;
        updatePlay();
        CompletableFuture
            .supplyAsync(() -> {
                final Process process = runAction.run(options);
                if (closeOnLaunch && process != null) {
                    closeOnLaunchAction.accept(process);
                }
                return process;
            }, executor)
            .whenComplete((process, error) -> SwingUtilities.invokeLater(() -> {
                if (process != null && options != null) {
                    runningClients.track(options.slug(), process);
                }
                running = false;
                updatePlay();
                if (error != null) {
                    // Send the error to the UncaughtErrorHandler, as before
                    throw unwrap(error);
                }
            }));
    }

    private static RuntimeException unwrap(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        return cause instanceof RuntimeException runtimeException ? runtimeException : new RuntimeException(cause);
    }

    private void setCanPlay(boolean canPlay) {
        this.canPlay = canPlay;
        updatePlay();
    }

    private void updatePlay() {
        play.setEnabled(canPlay && !running && !runningClients.isRunning(currentSlug));
        if (!play.isEnabled()) {
            stopCountdown();
        }
    }

    // Starts the countdown to play, if "Play on start" is selected and the client is ready
    void startPlayOnStart() {
        if (!playOnStart.isSelected() || !play.isEnabled()) {
            return;
        }
        remainingSeconds = PLAY_ON_START_SECONDS;
        counting = true;
        showCountdown();
        countdown.start();
    }

    void tick() {
        if (!counting) {
            return;
        }
        remainingSeconds--;
        if (remainingSeconds > 0) {
            showCountdown();
            return;
        }
        stopCountdown();
        if (play.isEnabled()) {
            play.doClick();
        }
    }

    private void showCountdown() {
        play.setText("%s (%d)".formatted(PLAY_TEXT, remainingSeconds));
    }

    private void stopCountdown() {
        counting = false;
        countdown.stop();
        play.setText(PLAY_TEXT);
    }

    private static JButton createButton(String text, float uiScaling) {
        JButton button = new JButton(text);
        button.setEnabled(false);
        setFontSize(button, ACTION_BUTTON_FONT_SIZE * uiScaling);
        return button;
    }

    static class VersionPanel extends JPanel {

        private final JLabel label;
        private final String text;

        VersionPanel(float uiScaling) {
            super(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            this.setBorder(BorderFactory.createEmptyBorder(0, SCALE, SCALE, SCALE));

            VersionDetails version = Environment.versionDetails();
            this.text = "Sweet v%s (%s)".formatted(version.version(), version.gitSha());

            this.label = new JLabel(text);
            label.setForeground(Color.GRAY);
            GuiComponents.setFontSize(label, (float) (BASE_FONT_SIZE * 0.9 * uiScaling));
            this.add(label);
        }

        String getText() {
            return label.getText();
        }

    }

    @Override
    public void loginStart() {
        setCanPlay(false);
    }

    @Override
    public void loginDone(ClientStatus client) {
        refresh(client);
    }

    @Override
    public void logoutDone() {
        setCanPlay(false);
    }

    @Override
    public void repairStart() {
        setCanPlay(false);
    }

    @Override
    public void repairDone(ClientStatus client) {
        setCanPlay(true);
    }

    @Override
    public void clientStarted(String slug) {
        updatePlay();
    }

    @Override
    public void clientStopped(String slug) {
        updatePlay();
    }

    @Override
    public void refresh(ClientStatus client) {
        currentSlug = client == null || client.client() == null ? null : client.client().slug();
        setCanPlay(client != null && client.validToken() && client.statusIs(UPDATED));
    }

}
