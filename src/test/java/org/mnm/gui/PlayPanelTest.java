package org.mnm.gui;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.mnm.client.RunnerOptions;
import org.mnm.client.RunnerOptions.LinuxOptions;
import org.mnm.config.Client;
import org.mnm.config.CursorSize;
import org.mnm.config.SettingsStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mnm.ApiServerStubs.TEST_SLUG;
import static org.mnm.config.Client.Status.NEEDS_UPDATE;
import static org.mnm.config.Client.Status.UPDATED;
import static org.mnm.config.SettingsStore.CLOSE_ON_PLAY_KEY;
import static org.mnm.config.SettingsStore.PLAY_ON_START_KEY;
import static org.mnm.gui.PlayPanel.PLAY_ON_START_SECONDS;
import static org.mnm.gui.ReflectionTestTools.get;
import static org.mnm.gui.ReflectionTestTools.getButton;

class PlayPanelTest {

    private JButton play;
    private JCheckBox closeOnPlay;
    private JCheckBox playOnStart;
    private final AtomicReference<Process> closedProcess = new AtomicReference<>();

    @Test
    void shouldDisablePlayByDefault() {
        initComponents();

        assertThat(play.isEnabled()).isFalse();
    }

    @Test
    void shouldInvokeAction() {
        final String randomSlug = UUID.randomUUID().toString();
        final AtomicReference<String> optionSupplied = new AtomicReference<>();
        final AtomicBoolean actionPerformed = new AtomicBoolean(false);

        PlayPanel playPanel = new PlayPanel(options -> {
            optionSupplied.set(options.slug());
            actionPerformed.set(true);
            return null;
        }, () -> new RunnerOptions(randomSlug, null, false, new LinuxOptions(false, false, null, new RunnerOptions.ProtonOptions(false, false), new RunnerOptions.WineOptions(CursorSize.DEFAULT))),
            new InMemorySettingsStore(Map.of()), closedProcess::set, 1f, Runnable::run);
        play = getButton(playPanel, "play");

        playPanel.refresh(new ClientStatus(client(UPDATED), true, null));
        play.doClick();

        assertThat(actionPerformed).isTrue();
        assertThat(optionSupplied).hasValue(randomSlug);
    }

    @Test
    void shouldEnablePlayWhenTokenIsValidAndClientIsUpToDate() {
        ClientStatus clientStatus = new ClientStatus(client(UPDATED), true, null);

        var panel = initComponents();
        panel.refresh(clientStatus);

        assertThat(play.isEnabled()).isTrue();
    }

    @Test
    void shouldDisablePlayWhenTokenIsNotValid() {
        ClientStatus clientStatus = new ClientStatus(null, false, null);

        var panel = initComponents();
        panel.refresh(clientStatus);

        assertThat(play.isEnabled()).isFalse();
    }

    @Test
    void shouldDisablePlayWhenTokenIsValidAndClientIsNotUpToDate() {
        ClientStatus clientStatus = new ClientStatus(client(NEEDS_UPDATE), true, null);

        var panel = initComponents();
        panel.refresh(clientStatus);

        assertThat(play.isEnabled()).isFalse();
    }

    @Test
    void shouldDisablePlayAfterLogout() {
        ClientStatus clientStatus = new ClientStatus(client(UPDATED), true, null);

        var panel = initComponents();
        panel.refresh(clientStatus);
        panel.logoutDone();

        assertThat(play.isEnabled()).isFalse();
    }

    @Test
    void shouldEnableAfterLoginWhenClientIsUpToDate() {
        ClientStatus clientStatus = new ClientStatus(client(UPDATED), true, null);

        var panel = initComponents();

        panel.loginStart();
        assertThat(play.isEnabled()).isFalse();

        panel.loginDone(clientStatus);
        assertThat(play.isEnabled()).isTrue();
    }

    @Test
    void shouldDisableAfterLoginWhenClientIsNotUpToDate() {
        ClientStatus clientStatus = new ClientStatus(client(NEEDS_UPDATE), true, null);

        var panel = initComponents();

        panel.loginStart();
        assertThat(play.isEnabled()).isFalse();

        panel.loginDone(clientStatus);
        assertThat(play.isEnabled()).isFalse();
    }

    @Test
    void shouldEnableAfterRepairWhenClientIsUpToDate() {
        ClientStatus clientStatus = new ClientStatus(client(UPDATED), true, null);

        var panel = initComponents();

        panel.repairStart();
        assertThat(play.isEnabled()).isFalse();

        panel.repairDone(clientStatus);
        assertThat(play.isEnabled()).isTrue();
    }

    @Test
    void shouldEnableAfterRepairWhenClientIsNotUpToDate() {
        ClientStatus clientStatus = new ClientStatus(client(NEEDS_UPDATE), true, null);

        var panel = initComponents();

        panel.repairStart();
        assertThat(play.isEnabled()).isFalse();

        panel.repairDone(clientStatus);
        assertThat(play.isEnabled()).isTrue();
    }

    @Test
    void shouldDisableCloseOnPlayByDefault() {
        initComponents();

        assertThat(closeOnPlay.isEnabled()).isFalse();
        assertThat(closeOnPlay.isSelected()).isFalse();
    }

    @Test
    void shouldEnableCloseOnPlayWhenPlayIsEnabled() {
        var panel = initComponents();
        panel.refresh(new ClientStatus(client(UPDATED), true, null));

        assertThat(closeOnPlay.isEnabled()).isTrue();
    }

    @Test
    void shouldDisableCloseOnPlayAfterLogout() {
        var panel = initComponents();
        panel.refresh(new ClientStatus(client(UPDATED), true, null));
        panel.logoutDone();

        assertThat(closeOnPlay.isEnabled()).isFalse();
    }

    @Test
    void shouldDisableCloseOnPlayAfterLoginStart() {
        var panel = initComponents();
        panel.refresh(new ClientStatus(client(UPDATED), true, null));
        panel.loginStart();

        assertThat(closeOnPlay.isEnabled()).isFalse();
    }

    @Test
    void shouldDisableCloseOnPlayAfterRepairStart() {
        var panel = initComponents();
        panel.refresh(new ClientStatus(client(UPDATED), true, null));
        panel.repairStart();

        assertThat(closeOnPlay.isEnabled()).isFalse();
    }

    @Test
    void shouldEnableCloseOnPlayAfterRepairDone() {
        var panel = initComponents();
        panel.repairStart();
        panel.repairDone(new ClientStatus(client(UPDATED), true, null));

        assertThat(closeOnPlay.isEnabled()).isTrue();
    }

    @Test
    void shouldPassProcessToCloseActionWhenCheckboxIsSelected() {
        final Process process = Mockito.mock(Process.class);
        var panel = initComponents(new InMemorySettingsStore(Map.of()), options -> process);

        panel.refresh(new ClientStatus(client(UPDATED), true, null));
        closeOnPlay.doClick();
        play.doClick();

        assertThat(closedProcess).hasValue(process);
    }

    @Test
    void shouldNotCallCloseActionWhenCheckboxIsNotSelected() {
        final Process process = Mockito.mock(Process.class);
        var panel = initComponents(new InMemorySettingsStore(Map.of()), options -> process);

        panel.refresh(new ClientStatus(client(UPDATED), true, null));
        play.doClick();

        assertThat(closedProcess).hasNullValue();
    }

    @Test
    void shouldStoreCloseOnPlayWhenCheckboxIsClicked() {
        final InMemorySettingsStore settingsStore = new InMemorySettingsStore(Map.of());
        initComponents(settingsStore, options -> null);

        play.setEnabled(true);
        closeOnPlay.doClick();

        assertThat(settingsStore.get(CLOSE_ON_PLAY_KEY)).isEqualTo("true");
    }

    @Test
    void shouldRestoreCloseOnPlayFromSettings() {
        initComponents(new InMemorySettingsStore(Map.of(CLOSE_ON_PLAY_KEY, "true")), options -> null);

        assertThat(closeOnPlay.isSelected()).isTrue();
    }

    @Test
    void shouldDisablePlayWhileClientRuns() {
        final AtomicReference<Runnable> task = new AtomicReference<>();
        var panel = initComponents(new InMemorySettingsStore(Map.of()), options -> null, task::set);
        panel.refresh(new ClientStatus(client(UPDATED), true, null));

        play.doClick();

        assertThat(task.get()).isNotNull();
        assertThat(play.isEnabled()).isFalse();
        assertThat(closeOnPlay.isEnabled()).isFalse();
    }

    @Test
    void shouldEnablePlayWhenClientStops() throws Exception {
        final AtomicReference<Runnable> task = new AtomicReference<>();
        var panel = initComponents(new InMemorySettingsStore(Map.of()), options -> null, task::set);
        panel.refresh(new ClientStatus(client(UPDATED), true, null));

        play.doClick();
        task.get().run();
        flushEventQueue();

        assertThat(play.isEnabled()).isTrue();
        assertThat(closeOnPlay.isEnabled()).isTrue();
    }

    @Test
    void shouldEnablePlayWhenClientFails() throws Exception {
        var panel = initComponents(new InMemorySettingsStore(Map.of()), options -> {
            throw new RuntimeException("client failed");
        });
        panel.refresh(new ClientStatus(client(UPDATED), true, null));

        play.doClick();
        flushEventQueue();

        assertThat(play.isEnabled()).isTrue();
    }

    @Test
    void shouldKeepPlayDisabledWhenLogoutHappensWhileClientRuns() throws Exception {
        final AtomicReference<Runnable> task = new AtomicReference<>();
        var panel = initComponents(new InMemorySettingsStore(Map.of()), options -> null, task::set);
        panel.refresh(new ClientStatus(client(UPDATED), true, null));

        play.doClick();
        panel.logoutDone();
        task.get().run();
        flushEventQueue();

        assertThat(play.isEnabled()).isFalse();
    }

    @Test
    void shouldNotEnablePlayWhenRefreshHappensWhileClientRuns() {
        final AtomicReference<Runnable> task = new AtomicReference<>();
        var panel = initComponents(new InMemorySettingsStore(Map.of()), options -> null, task::set);
        panel.refresh(new ClientStatus(client(UPDATED), true, null));

        play.doClick();
        panel.refresh(new ClientStatus(client(UPDATED), true, null));

        assertThat(play.isEnabled()).isFalse();
    }

    @Test
    void shouldNotSelectPlayOnStartByDefault() {
        initComponents();

        assertThat(playOnStart.isSelected()).isFalse();
    }

    @Test
    void shouldRestorePlayOnStartFromSettings() {
        initComponents(new InMemorySettingsStore(Map.of(PLAY_ON_START_KEY, "true")), options -> null);

        assertThat(playOnStart.isSelected()).isTrue();
    }

    @Test
    void shouldStorePlayOnStartWhenCheckboxIsClicked() {
        final InMemorySettingsStore settingsStore = new InMemorySettingsStore(Map.of());
        initComponents(settingsStore, options -> null);

        playOnStart.doClick();

        assertThat(settingsStore.get(PLAY_ON_START_KEY)).isEqualTo("true");
    }

    @Test
    void shouldPlayAfterCountdownWhenPlayOnStartIsSelected() {
        final AtomicInteger runs = new AtomicInteger();
        var panel = initPlayOnStart(runs);

        panel.startPlayOnStart();
        stopTimer(panel);
        assertThat(play.getText()).isEqualTo("Play (" + PLAY_ON_START_SECONDS + ")");

        tick(panel, PLAY_ON_START_SECONDS - 1);
        assertThat(play.getText()).isEqualTo("Play (1)");
        assertThat(runs.get()).isZero();

        panel.tick();
        assertThat(runs.get()).isEqualTo(1);
        assertThat(play.getText()).isEqualTo("Play");
    }

    @Test
    void shouldNotPlayOnStartWhenOptionIsNotSelected() {
        final AtomicInteger runs = new AtomicInteger();
        var panel = initComponents(new InMemorySettingsStore(Map.of()), options -> {
            runs.incrementAndGet();
            return null;
        });
        panel.refresh(new ClientStatus(client(UPDATED), true, null));

        panel.startPlayOnStart();
        tick(panel, PLAY_ON_START_SECONDS);

        assertThat(runs.get()).isZero();
        assertThat(play.getText()).isEqualTo("Play");
    }

    @Test
    void shouldNotPlayOnStartWhenPlayIsDisabled() {
        final AtomicInteger runs = new AtomicInteger();
        var panel = initComponents(new InMemorySettingsStore(Map.of(PLAY_ON_START_KEY, "true")), options -> {
            runs.incrementAndGet();
            return null;
        });
        panel.refresh(new ClientStatus(client(NEEDS_UPDATE), true, null));

        panel.startPlayOnStart();
        tick(panel, PLAY_ON_START_SECONDS);

        assertThat(runs.get()).isZero();
        assertThat(play.getText()).isEqualTo("Play");
    }

    @Test
    void shouldCancelPlayOnStartWhenOptionIsCleared() {
        final AtomicInteger runs = new AtomicInteger();
        var panel = initPlayOnStart(runs);

        panel.startPlayOnStart();
        stopTimer(panel);
        playOnStart.doClick();
        tick(panel, PLAY_ON_START_SECONDS);

        assertThat(runs.get()).isZero();
        assertThat(play.getText()).isEqualTo("Play");
    }

    @Test
    void shouldCancelPlayOnStartWhenPlayIsDisabled() {
        final AtomicInteger runs = new AtomicInteger();
        var panel = initPlayOnStart(runs);

        panel.startPlayOnStart();
        stopTimer(panel);
        panel.logoutDone();
        tick(panel, PLAY_ON_START_SECONDS);

        assertThat(runs.get()).isZero();
        assertThat(play.getText()).isEqualTo("Play");
    }

    @Test
    void shouldPlayOnceWhenPlayIsClickedDuringCountdown() throws Exception {
        final AtomicInteger runs = new AtomicInteger();
        var panel = initPlayOnStart(runs);

        panel.startPlayOnStart();
        stopTimer(panel);
        play.doClick();
        flushEventQueue();
        tick(panel, PLAY_ON_START_SECONDS);

        assertThat(runs.get()).isEqualTo(1);
        assertThat(play.getText()).isEqualTo("Play");
    }

    @Test
    void shouldDisablePlayWhileGameRunsAndEnableAfterItExits() throws Exception {
        var exit = new CompletableFuture<Process>();
        var panel = panelWithProcess(exit);
        panel.refresh(new ClientStatus(client(UPDATED), true, null));

        play.doClick();
        flushEventQueue();

        assertThat(play.isEnabled()).isFalse();
        // The game still runs: refresh must not enable "Play"
        panel.refresh(new ClientStatus(client(UPDATED), true, null));
        assertThat(play.isEnabled()).isFalse();

        exit.complete(null);
        waitUntil(() -> play.isEnabled());
    }

    @Test
    void shouldKeepPlayDisabledAfterGameExitsWhenClientIsNotReady() throws Exception {
        var exit = new CompletableFuture<Process>();
        var panel = panelWithProcess(exit);
        panel.refresh(new ClientStatus(client(UPDATED), true, null));

        play.doClick();
        flushEventQueue();
        panel.refresh(new ClientStatus(client(NEEDS_UPDATE), true, null));
        exit.complete(null);
        flushEventQueue();
        flushEventQueue();

        assertThat(play.isEnabled()).isFalse();
    }

    @Test
    void shouldEnablePlayForAnotherSlugWhileGameRuns() throws Exception {
        var exit = new CompletableFuture<Process>();
        var panel = panelWithProcess(exit);
        panel.refresh(new ClientStatus(client(UPDATED), true, null));

        play.doClick();
        flushEventQueue();
        assertThat(play.isEnabled()).isFalse();

        panel.refresh(new ClientStatus(new Client("other", "1.2.3", UPDATED, Path.of(".")), true, null));
        assertThat(play.isEnabled()).isTrue();

        // Back to the running client
        panel.refresh(new ClientStatus(client(UPDATED), true, null));
        assertThat(play.isEnabled()).isFalse();
    }

    private PlayPanel panelWithProcess(CompletableFuture<Process> exit) {
        Process process = Mockito.mock(Process.class);
        Mockito.when(process.onExit()).thenReturn(exit);
        var panel = new PlayPanel(options -> process, () -> runnerOptions(TEST_SLUG),
            new InMemorySettingsStore(Map.of()), closedProcess::set, 1f, Runnable::run);
        play = getButton(panel, "play");
        return panel;
    }

    private static RunnerOptions runnerOptions(String slug) {
        return new RunnerOptions(slug, null, false, new LinuxOptions(false, false, null,
            new RunnerOptions.ProtonOptions(false, false), new RunnerOptions.WineOptions(CursorSize.DEFAULT)));
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            flushEventQueue();
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private PlayPanel initPlayOnStart(AtomicInteger runs) {
        var panel = initComponents(new InMemorySettingsStore(Map.of(PLAY_ON_START_KEY, "true")), options -> {
            runs.incrementAndGet();
            return null;
        });
        panel.refresh(new ClientStatus(client(UPDATED), true, null));
        return panel;
    }

    // The test calls tick() directly, so the real timer must not run
    private static void stopTimer(PlayPanel panel) {
        ((Timer) get(panel, "countdown")).stop();
    }

    private static void tick(PlayPanel panel, int times) {
        for (int i = 0; i < times; i++) {
            panel.tick();
        }
    }

    private static void flushEventQueue() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
        });
    }

    private PlayPanel initComponents() {
        return initComponents(new InMemorySettingsStore(Map.of()), options -> null);
    }

    private PlayPanel initComponents(SettingsStore settingsStore, GuiCommand.PlayAction action) {
        return initComponents(settingsStore, action, Runnable::run);
    }

    private PlayPanel initComponents(SettingsStore settingsStore, GuiCommand.PlayAction action, Executor executor) {
        PlayPanel playPanel = new PlayPanel(action, () -> null, settingsStore, closedProcess::set, 1f, executor);
        play = getButton(playPanel, "play");
        closeOnPlay = (JCheckBox) get(playPanel, "closeOnPlay");
        playOnStart = (JCheckBox) get(playPanel, "playOnStart");
        return playPanel;
    }

    private static Client client(Client.Status updated) {
        return new Client(TEST_SLUG, "1.2.3", updated, Path.of("."));
    }
}
