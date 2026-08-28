package com.luna.ausm.impl.client;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.Reference;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.event.ClickEvent;

/** Performs one asynchronous, client-only release check after the first world load. */
public final class AusmUpdateChecker {
    private static final String RELEASES_URL = "https://api.github.com/repos/MtcLuna05/AUSM/releases?per_page=100";
    private static final String CURSEFORGE_PAGE_URL = "https://www.curseforge.com/minecraft/mc-mods/ausm";
    private static final String RUNTIME_SUFFIX = "-Java25";
    private static final Pattern TAG_NAME = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern RELEASE_VERSION = Pattern.compile("(?:AUSM-)?v?([0-9]+(?:\\.[0-9]+){0,2}(?:-[0-9A-Za-z.-]+)?)" + Pattern.quote(RUNTIME_SUFFIX));
    private static volatile boolean checkStarted;

    private AusmUpdateChecker() {
    }

    public static void checkAfterWorldLoad() {
        ClientSettingsConfig config = MainMod.getClientSettingsConfig();
        if (config == null || !config.updateCheckerEnabled() || checkStarted) {
            return;
        }
        synchronized (AusmUpdateChecker.class) {
            if (checkStarted) {
                return;
            }
            checkStarted = true;
        }

        Thread worker = new Thread(AusmUpdateChecker::checkForNewerRelease, "AUSM update checker");
        worker.setDaemon(true);
        worker.start();
    }

    private static void checkForNewerRelease() {
        try {
            String latest = fetchLatestReleaseVersion();
            if (latest != null && SemanticVersion.parse(latest).compareTo(SemanticVersion.parse(Reference.VERSION)) > 0) {
                scheduleNotification(Reference.VERSION, latest);
            }
        } catch (Exception e) {
            // CurseForge's release API requires a private API key, so GitHub is the public source of truth.
            MainMod.LOGGER.warn("[UpdateChecker] Failed to check for AUSM releases", e);
        }
    }

    private static String fetchLatestReleaseVersion() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(RELEASES_URL).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(5_000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "AUSM-Update-Checker/" + Reference.VERSION);
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("GitHub releases returned HTTP " + status);
            }
            String response = readResponse(connection);
            Matcher tags = TAG_NAME.matcher(response);
            SemanticVersion highest = null;
            while (tags.find()) {
                Matcher version = RELEASE_VERSION.matcher(tags.group(1));
                if (!version.matches()) {
                    continue;
                }
                SemanticVersion candidate = SemanticVersion.parse(version.group(1));
                if (highest == null || candidate.compareTo(highest) > 0) {
                    highest = candidate;
                }
            }
            return highest == null ? null : highest.toString();
        } finally {
            connection.disconnect();
        }
    }

    private static String readResponse(HttpURLConnection connection) throws IOException {
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            char[] buffer = new char[2048];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                response.append(buffer, 0, read);
            }
        }
        return response.toString();
    }

    private static void scheduleNotification(String current, String latest) {
        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        if (minecraft != null) {
            MinecraftReflectionCompat.addScheduledTask(minecraft, () -> postNotification(current, latest));
        }
    }

    private static void postNotification(String current, String latest) {
        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        GuiIngame ingameGui = minecraft == null ? null : MinecraftReflectionCompat.field(minecraft, GuiIngame.class, null, "field_71456_v", "ingameGUI");
        GuiNewChat chat = ingameGui == null ? null : MinecraftReflectionCompat.call(ingameGui, GuiNewChat.class, null,
                new String[]{"func_146158_b", "getChatGUI"}, MinecraftReflectionCompat.NO_PARAMETERS);
        if (chat == null) {
            return;
        }
        ITextComponent message = new TextComponentString("[AUSM] New AUSM version available! Current: " + current + ", Latest: " + latest);
        message.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, CURSEFORGE_PAGE_URL));
        MinecraftReflectionCompat.invoke(chat, new String[]{"func_146227_a", "printChatMessage"}, new Class<?>[]{ITextComponent.class}, message);
    }

    private record SemanticVersion(int major, int minor, int patch, String qualifier) implements Comparable<SemanticVersion> {
        private static SemanticVersion parse(String value) {
            String[] split = value.split("-", 2);
            String[] numbers = split[0].split("\\.");
            return new SemanticVersion(number(numbers, 0), number(numbers, 1), number(numbers, 2), split.length == 2 ? split[1] : "");
        }

        private static int number(String[] values, int index) {
            return index < values.length ? Integer.parseInt(values[index]) : 0;
        }

        @Override
        public int compareTo(SemanticVersion other) {
            int majorComparison = Integer.compare(major, other.major);
            if (majorComparison != 0) return majorComparison;
            int minorComparison = Integer.compare(minor, other.minor);
            if (minorComparison != 0) return minorComparison;
            int patchComparison = Integer.compare(patch, other.patch);
            if (patchComparison != 0) return patchComparison;
            if (qualifier.isEmpty() != other.qualifier.isEmpty()) return qualifier.isEmpty() ? 1 : -1;
            return qualifier.compareTo(other.qualifier);
        }

        @Override
        public String toString() {
            return major + "." + minor + "." + patch + (qualifier.isEmpty() ? "" : "-" + qualifier);
        }
    }
}
