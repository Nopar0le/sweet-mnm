package org.mnm.tools;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mnm.config.Environment.getWorkDir;

class ProcessUtilsTest {

    private static final String SECRET_TOKEN = "secret-token";
    // Java does not start a command with a null character, and the error is not "command not found"
    private static final String[] INVALID_COMMAND = {"tool", "bad\u0000arg", "--token", SECRET_TOKEN};

    @Test
    void shouldHandleMissingCommand() {
        assertThatThrownBy(() -> ProcessUtils.run(getWorkDir(), new String[]{"no-no-no", "arg0", "arg-two"}))
            .isInstanceOf(CommandNotFound.class)
            .hasMessage("no-no-no")
            .hasCauseInstanceOf(IOException.class);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void shouldHandleUnexpectedError() {
        assertThatThrownBy(() -> ProcessUtils.run(getWorkDir(), new String[]{"ls", "thingy"}))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Process failed: exitCode=2, stdout=, stderr=ls: cannot access 'thingy': No such file or directory")
            .hasNoCause();
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void shouldRunCommand() {
        String output = ProcessUtils.run(getWorkDir(), new String[]{"ls", "-la"});

        assertThat(output)
            .contains("src")
            .contains("build.gradle")
            .contains("settings.gradle");
    }

    @Test
    void shouldHandleMissingCommandOnStart() {
        assertThatThrownBy(() -> ProcessUtils.start(getWorkDir(), new String[]{"no-no-no", "arg0"}, Map.of()))
            .isInstanceOf(CommandNotFound.class)
            .hasMessage("no-no-no")
            .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void shouldNotShowTokenWhenRunFails() {
        assertThatThrownBy(() -> ProcessUtils.run(getWorkDir(), INVALID_COMMAND))
            .hasMessageEndingWith("--token ***")
            .hasMessageNotContaining(SECRET_TOKEN);
    }

    @Test
    void shouldNotShowTokenWhenStartFails() {
        assertThatThrownBy(() -> ProcessUtils.start(getWorkDir(), INVALID_COMMAND, Map.of()))
            .hasMessageEndingWith("--token ***")
            .hasMessageNotContaining(SECRET_TOKEN);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void shouldStartCommand() throws Exception {
        Process process = ProcessUtils.start(getWorkDir(), new String[]{"ls", "-la"}, Map.of());

        assertThat(process.waitFor()).isZero();
    }
}
