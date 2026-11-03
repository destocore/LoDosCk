package NetShield.loDosCk;

import com.google.inject.Inject;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class LoDosCk {

    private final Logger logger;
    private final KeenEyeAI keenEye;
    private final Path dataDir;
    private final ProxyServer server;

    private CommentedConfigurationNode config;

    private String chatKickMsg;
    private String warningMsg;

    @Inject
    public LoDosCk(ProxyServer server, Logger logger, @DataDirectory Path dataDir) {
        this.logger = logger;
        this.dataDir = dataDir;
        this.server = server;
        this.keenEye = new KeenEyeAI(server, logger, dataDir);
    }

    private double getMemoryUsagePercentage() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        double percentage = ((double) usedMemory / maxMemory) * 100;
        return Math.round(percentage * 100.0) / 100.0;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        try {
            if (Files.notExists(dataDir)) {
                Files.createDirectories(dataDir);
            }

            Path configFile = dataDir.resolve("config.yml");

            if (Files.notExists(configFile)) {
                try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
                    if (in != null) {
                        Files.copy(in, configFile);
                    } else {
                        Files.createFile(configFile);
                    }
                }
            }

            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(configFile)
                    .build();

            this.config = loader.load();
            logger.info("[LoDosCk] Pipeline core metrics successfully loaded.");

        } catch (ConfigurateException e) {
            logger.error("[LoDosCk] Critical config.yml parsing logic syntax exception!", e);
        } catch (IOException e) {
            logger.error("[LoDosCk] General IO failure allocating internal file maps!", e);
        }

        final double maxRam = this.config.node("memory-guard", "critical-ram-percentage").getDouble(95.0);

        server.getScheduler()
                .buildTask(this, () -> {
                    double ramUsed = getMemoryUsagePercentage();
                    if (ramUsed > maxRam) {
                        logger.warn("[🚨 LoDosCk] RAM SATURATION: {}% / {}% reached. Purging transient heaps.", ramUsed, maxRam);
                        keenEye.cleanRAM();
                    }
                })
                .repeat(java.time.Duration.ofSeconds(5))
                .delay(java.time.Duration.ofSeconds(1))
                .schedule();

        server.getScheduler().buildTask(
                        this, keenEye::SendIPs2BanListServer)
                .repeat(java.time.Duration.ofSeconds(30))
                .delay(java.time.Duration.ofSeconds(30))
                .schedule();

        chatKickMsg = this.config.node("messages", "chat-flood-kick").getString("§c[LoDosCk] You were kicked for aggressive chat flooding (Limit: 2 repeats per 5 sec).");
        warningMsg = this.config.node("messages", "chat-flood-warn").getString("§c[LoDosCk] Please refrain from repeating identical messages so frequently.");

        logger.info("""
                
                ===========================================================================
                 █████                ██████████                      █████████  █████    \s
                ░░███                ░░███░░░░███                    ███░░░░░███░░███     \s
                 ░███         ██████  ░███   ░░███  ██████   █████  ███     ░░░  ░███ █████
                 ░███        ███░░███ ░███    ░███ ███░░███ ███░░  ░███          ░███░░███\s
                 ░███       ░███ ░███ ░███    ░███░███ ░███░░█████ ░███          ░██████░ \s
                 ░███      █░███ ░███ ░███    ███ ░███ ░███ ░░░░███░░███     ███ ░███░░███\s
                 ███████████░░██████  ██████████  ░░██████  ██████  ░░█████████  ████ █████
                ░░░░░░░░░░░  ░░░░░░  ░░░░░░░░░░    ░░░░░░  ░░░░░░    ░░░░░░░░░  ░░░░ ░░░░░\s
                ===========================================================================
                Core processing protection pipeline active!
                ===========================================================================
                """);
        System.gc();
    }

    @Subscribe
    public void ChatSend(PlayerChatEvent pce) {
        Player[] suspects = keenEye.CheckMsg(pce);

        if (suspects != null) {
            pce.setResult(PlayerChatEvent.ChatResult.denied());

            if (suspects.length == 1) {
                suspects[0].sendMessage(Component.text(warningMsg));
                return;
            }

            if (suspects.length == 3) {
                suspects[0].disconnect(Component.text(chatKickMsg));
                return;
            }
            if (suspects.length == 4) {
                Player cleverBot = suspects[0];
                cleverBot.disconnect(Component.text(chatKickMsg));

                logger.warn("[LoDosCk] Packet data mirroring interception. Connection terminated safely for: {}",
                        cleverBot.getUsername());
                return;
            }
            if (suspects.length == 2) {
                Player currentBot = suspects[0];
                Player firstBot = suspects[1];

                currentBot.disconnect(Component.text(chatKickMsg));

                if (firstBot.isActive()) {
                    firstBot.disconnect(Component.text(chatKickMsg));
                }

                logger.warn("[LoDosCk] Distributed cluster anomaly eliminated. Node {} cloned profile payload signature from {}.",
                        currentBot.getUsername(), firstBot.getUsername());
            }
        }
    }

    @Subscribe
    public void JoinSend(PreLoginEvent ple){
        keenEye.CheckJoin(ple);
    }

    @Subscribe
    public void onPlayerQuit(com.velocitypowered.api.event.connection.DisconnectEvent event) {
        String username = event.getPlayer().getUsername();
        String ip = event.getPlayer().getRemoteAddress().getAddress().getHostAddress();

        keenEye.removePlayer(username, ip);
    }
}