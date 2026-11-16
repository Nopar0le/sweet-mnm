package org.mnm.gui;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

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

        PlayPanel playPanel = new PlayPanel((options, closeOnLaunch) -> {
            optionSupplied.set(options.slug());
            actionPerformed.set(true);
        }, () -> new RunnerOptions(randomSlug, null, false, new LinuxOptions(false, false, null, new RunnerOptions.ProtonOptions(false, false), new RunnerOptions.WineOptions(CursorSize.DEFAULT))),
            new InMemorySettingsStore(Map.of()), 1f, Runnable::run);
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
    void shouldPassCloseOnLaunchTrueWhenCheckboxIsSelected() {
        final AtomicReference<Boolean> closeOnLaunch = new AtomicReference<>();
        var panel = initComponents(new InMemorySettingsStore(Map.of()), (options, close) -> closeOnLaunch.set(close));

        panel.refresh(new ClientStatus(client(UPDATED), true, null));
        closeOnPlay.doClick();
        play.doClick();

        assertThat(closeOnLaunch).hasValue(true);
    }

    @Test
    void shouldPassCloseOnLaunchFalseWhenCheckboxIsNotSelected() {
        final AtomicReference<Boolean> closeOnLaunch = new AtomicReference<>();
        var panel = initComponents(new InMemorySettingsStore(Map.of()), (options, close) -> closeOnLaunch.set(close));

        panel.refresh(new ClientStatus(client(UPDATED), true, null));
        play.doClick();

        assertThat(closeOnLaunch).hasValue(false);
    }

    @Test
    void shouldStoreCloseOnPlayWhenCheckboxIsClicked() {
        final InMemorySettingsStore settingsStore = new InMemorySettingsStore(Map.of());
        initComponents(settingsStore, (options, close) -> {
        });

        play.setEnabled(true);
        closeOnPlay.doClick();

        assertThat(settingsStore.get(CLOSE_ON_PLAY_KEY)).isEqualTo("true");
    }

    @Test
    void shouldRestoreCloseOnPlayFromSettings() {
        initComponents(new InMemorySettingsStore(Map.of(CLOSE_ON_PLAY_KEY, "true")), (options, close) -> {
        });

        assertThat(closeOnPlay.isSelected()).isTrue();
    }

    @Test
    void shouldDisablePlayWhileClientRuns() {
        final AtomicReference<Runnable> task = new AtomicReference<>();
        var panel = initComponents(new InMemorySettingsStore(Map.of()), (options, close) -> {
        }, task::set);
        panel.refresh(new ClientStatus(client(UPDATED), true, null));

        play.doClick();

        assertThat(task.get()).isNotNull();
        assertThat(play.isEnabled()).isFalse();
        assertThat(closeOnPlay.isEnabled()).isFalse();
    }

    @Test
    void shouldEnablePlayWhenClientStops() throws Exception {
        final AtomicReference<Runnable> task = new AtomicReference<>();
        var panel = initComponents(new InMemorySettingsStore(Map.of()), (options, close) -> {
        }, task::set);
        panel.refresh(new ClientStatus(client(UPDATED), true, null));

        play.doClick();
        task.get().run();
        flushEventQueue();

        assertThat(play.isEnabled()).isTrue();
        assertThat(closeOnPlay.isEnabled()).isTrue();
    }

    @Test
    void shouldEnablePlayWhenClientFails() throws Exception {
        var panel = initComponents(new InMemorySettingsStore(Map.of()), (options, close) -> {
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
        var panel = initComponents(new InMemorySettingsStore(Map.of()), (options, close) -> {
        }, task::set);
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
        var panel = initComponents(new InMemorySettingsStore(Map.of()), (options, close) -> {
        }, task::set);
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
        initComponents(new InMemorySettingsStore(Map.of(PLAY_ON_START_KEY, "true")), (options, close) -> {
        });

        assertThat(playOnStart.isSelected()).isTrue();
    }

    @Test
    void shouldStorePlayOnStartWhenCheckboxIsClicked() {
        final InMemorySettingsStore settingsStore = new InMemorySettingsStore(Map.of());
        initComponents(settingsStore, (options, close) -> {
        });

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
        var panel = initComponents(new InMemorySettingsStore(Map.of()), (options, close) -> runs.incrementAndGet());
        panel.refresh(new ClientStatus(client(UPDATED), true, null));

        panel.startPlayOnStart();
        tick(panel, PLAY_ON_START_SECONDS);

        assertThat(runs.get()).isZero();
        assertThat(play.getText()).isEqualTo("Play");
    }

    @Test
    void shouldNotPlayOnStartWhenPlayIsDisabled() {
        final AtomicInteger runs = new AtomicInteger();
        var panel = initComponents(new InMemorySettingsStore(Map.of(PLAY_ON_START_KEY, "true")), (options, close) -> runs.incrementAndGet());
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

    private PlayPanel initPlayOnStart(AtomicInteger runs) {
        var panel = initComponents(new InMemorySettingsStore(Map.of(PLAY_ON_START_KEY, "true")), (options, close) -> runs.incrementAndGet());
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
        return initComponents(new InMemorySettingsStore(Map.of()), (options, closeOnLaunch) -> {
        });
    }

    private PlayPanel initComponents(SettingsStore settingsStore, GuiCommand.PlayAction action) {
        return initComponents(settingsStore, action, Runnable::run);
    }

    private PlayPanel initComponents(SettingsStore settingsStore, GuiCommand.PlayAction action, Executor executor) {
        PlayPanel playPanel = new PlayPanel(action, () -> null, settingsStore, 1f, executor);
        play = getButton(playPanel, "play");
        closeOnPlay = (JCheckBox) get(playPanel, "closeOnPlay");
        playOnStart = (JCheckBox) get(playPanel, "playOnStart");
        return playPanel;
    }

    private static Client client(Client.Status updated) {
        return new Client(TEST_SLUG, "1.2.3", updated, Path.of("."));
    }
}
