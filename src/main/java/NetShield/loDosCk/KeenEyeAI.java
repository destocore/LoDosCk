package NetShield.loDosCk;

import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class KeenEyeAI {
    private static final String ENGLISH_GL = "aeiouy";
    private static final String ENGLISH_CN = "bcdfghjklmnpqrstvwxyz";

    private static final String[] ENGLISH_CN_TRIO = {"sch", "chr", "thr", "str", "phr", "tch", "gth"};
    private static final String[] ENGLISH_CN_DUO = {
            "ch", "sh", "th", "ph", "wh", "ck", "ng", "gh", "kn", "wr", "gn", "mb",
            "ss", "tt", "ll", "pp", "cc", "ff", "gg", "mm", "nn", "rr", "bb", "dd",
            "sc", "sk", "sl", "sm", "sn", "sp", "st", "sw", "tr", "tw", "pl", "fr"
    };

    private static final String[] ENGLISH_GL_TRIO = {"eau", "iou", "aye"};
    private static final String[] ENGLISH_GL_DUO = {"ee", "oo", "ea", "ai", "ou", "oi", "ae", "ay", "ey", "oa", "ui"};

    private final Map<String, Player> MsgHis = new ConcurrentHashMap<>();
    private final Set<String> underObservation = ConcurrentHashMap.newKeySet();

    private final Map<String, Integer> suspiciousQuota = new ConcurrentHashMap<>();
    private final Map<String, Long> floodTracker = new ConcurrentHashMap<>();
    private final Map<String, String> playersIp = new ConcurrentHashMap<>();

    private final Set<String> banQueue = ConcurrentHashMap.newKeySet();
    private final Set<String> ipBanList = ConcurrentHashMap.newKeySet();

    private final ProxyServer server;
    private final Logger logger;
    private CommentedConfigurationNode config;

    private final Path dataD;

    private final Integer maxLenNick;
    private final Integer maxConsecutiveConsonants;
    private final Integer maxConsecutiveVowels;

    private final Integer maxSuspicious;
    private final Integer MaxPlayers;

    private final Integer modeAI;
    private final String banReason;
    private final String kickReason;

    public KeenEyeAI(ProxyServer server, Logger logger, Path dataDir) {
        this.server = server;
        this.logger = logger;
        this.dataD = dataDir;

        try {
            Path configFile = dataDir.resolve("config.yml");
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(configFile)
                    .build();

            this.config = loader.load();
            logger.info("[KeenEyeAI] Configuration parameters loaded successfully.");

        } catch (ConfigurateException e) {
            logger.error("[KeenEyeAI] Critical error parsed tracking config.yml syntax!", e);
        }

        assert this.config != null;

        maxLenNick = this.config.node("nick-analyzer","max-unstructured-length").getInt(14);
        maxConsecutiveConsonants = this.config.node("nick-analyzer","max-consecutive-consonants").getInt(4);
        maxConsecutiveVowels = this.config.node("nick-analyzer","max-consecutive-vowels").getInt(4);

        modeAI = this.config.node("KeenEyeAI", "mode").getInt(2);
        maxSuspicious = this.config.node("IP-guard","max-suspicious").getInt(1);
        MaxPlayers = this.config.node("IP-guard", "max").getInt(3);

        kickReason = this.config.node("messages", "emergency-kick").getString("&c[LoDosCk] Proxy gateway overloaded. Please re-authenticate in a few moments.");
        banReason = this.config.node("messages", "ban-reason").getString("§c[LoDosCk] Banned");

        try {
            if (Files.notExists(dataD)) {
                Files.createDirectories(dataD);
            }
            Path banFile = dataD.resolve("banlist.txt");
            if (Files.notExists(banFile)) {
                Files.createFile(banFile);
            }

            List<String> lines = Files.readAllLines(banFile);
            for (String ip : lines) {
                if (!ip.trim().isEmpty()) {
                    ipBanList.add(ip.trim());
                }
            }
            logger.info("[LoDosCk] Cached {} blacklisted IP nodes into gateway RAM.", ipBanList.size());
        } catch (IOException e) {
            logger.error("[LoDosCk] Failed to initialize local persistent ban tables!", e);
        }
    }

    public void CheckJoin(PreLoginEvent ple) {
        String pureIP = ple.getConnection().getRemoteAddress().getAddress().getHostAddress();

        if (InBanList(pureIP)) {
            ple.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    Component.text(banReason)
            ));
            return;
        }

        if (modeAI < 1) return;

        String rawNick = ple.getUsername().toLowerCase();

        long countPlr = playersIp.values().stream().filter(ip -> ip.equals(pureIP)).count();
        if (countPlr >= MaxPlayers) {
            ple.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    Component.text("&c[LoDosCk] Too many connections from your IP address.")
            ));
            logger.warn("[LoDosCk] Network allocation warning: IP {} hit threshold limits ({}/{})", pureIP, countPlr, MaxPlayers);
            return;
        }

        boolean isSuspicious = rawNick.length() >= maxLenNick && !rawNick.contains("_") && !rawNick.contains("-");

        if (!isSuspicious) {
            String nick = rawNick.replaceAll("[\\d_-]", "");
            for (String trio : ENGLISH_CN_TRIO) nick = nick.replace(trio, "a");
            for (String trio : ENGLISH_GL_TRIO) nick = nick.replace(trio, "b");
            for (String duo : ENGLISH_CN_DUO)   nick = nick.replace(duo, "a");
            for (String duo : ENGLISH_GL_DUO)   nick = nick.replace(duo, "b");

            int consecutiveConsonants = 0;
            int consecutiveVowels = 0;

            for (int i = 0; i < nick.length(); i++) {
                char ch = nick.charAt(i);

                if (ENGLISH_CN.indexOf(ch) != -1) {
                    consecutiveConsonants++;
                    consecutiveVowels = 0;
                    if (consecutiveConsonants >= maxConsecutiveConsonants) {
                        isSuspicious = true;
                        break;
                    }
                } else if (ENGLISH_GL.indexOf(ch) != -1) {
                    consecutiveVowels++;
                    consecutiveConsonants = 0;
                    if (consecutiveVowels >= maxConsecutiveVowels) {
                        isSuspicious = true;
                        break;
                    }
                } else {
                    consecutiveConsonants = 0;
                    consecutiveVowels = 0;
                }
            }
        }

        if (isSuspicious) {
            int currentSuspiciousCount = suspiciousQuota.getOrDefault(pureIP, 0);

            if (currentSuspiciousCount >= maxSuspicious) {
                ple.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                        Component.text("&c[LoDosCk] DDoS threat mitigation active. Connection refused.")
                ));
                logger.error("[LoDosCk] Intrusion blocked: Signature ratio hit limits ({}/{}) for node IP: {}.",
                        currentSuspiciousCount + 1, maxSuspicious, pureIP);
                return;
            }

            suspiciousQuota.put(pureIP, currentSuspiciousCount + 1);

            underObservation.add(rawNick);
            logger.warn("[LoDosCk] Structural anomaly detected: {} ({}) flagged for context monitoring. Quota: {}/{}",
                    ple.getUsername(), pureIP, currentSuspiciousCount + 1, maxSuspicious);
        } else {
            playersIp.put(rawNick, pureIP);
        }
    }

    public Player[] CheckMsg(PlayerChatEvent pce) {
        if (modeAI < 2) return null;
        Player currentPlayer = pce.getPlayer();
        String currentNick = currentPlayer.getUsername().toLowerCase();
        String cleanMsg = pce.getMessage().toLowerCase().replaceAll("[\\s.,()_*-]", "");

        if (MsgHis.containsKey(cleanMsg)) {
            Player firstPlayer = MsgHis.get(cleanMsg);
            if (firstPlayer.equals(currentPlayer)) {
                long currentTime = System.currentTimeMillis();
                if (floodTracker.containsKey(currentNick)) {
                    long lastFloodTime = floodTracker.get(currentNick);
                    if (currentTime - lastFloodTime < 5000) {
                        if (underObservation.contains(currentNick)) {
                            return new Player[]{currentPlayer, currentPlayer, currentPlayer};
                        }
                        floodTracker.put(currentNick, currentTime);
                        return new Player[]{currentPlayer};
                    }
                }
                floodTracker.put(currentNick, currentTime);
                return null;
            }

            if (underObservation.contains(currentNick) || underObservation.contains(firstPlayer.getUsername().toLowerCase())) {
                MsgHis.remove(cleanMsg);

                String pureIP = currentPlayer.getRemoteAddress().getAddress().getHostAddress();
                if (pureIP != null) {
                    banQueue.add(pureIP);
                    ipBanList.add(pureIP);
                }
                return new Player[]{currentPlayer, firstPlayer};
            }
            MsgHis.remove(cleanMsg);
            return new Player[]{currentPlayer, currentPlayer, currentPlayer, currentPlayer};
        }
        if (MsgHis.size() > 100) {
            MsgHis.clear();
        }
        MsgHis.put(cleanMsg, currentPlayer);
        return null;
    }
    public void cleanRAM() {
        if (underObservation.isEmpty()) return;
        floodTracker.keySet().removeIf(underObservation::contains);
        for (String botName : underObservation) {
            server.getPlayer(botName).ifPresent(botPlayer -> botPlayer.disconnect(Component.text(kickReason)));
        }
        underObservation.clear();
        suspiciousQuota.clear();
        logger.info("[LoDosCk] Cache metrics map garbage collected successfully.");
    }
    public void removePlayer(String username, String ip) {
        String nick = username.toLowerCase();
        underObservation.remove(nick);
        floodTracker.remove(nick);
        playersIp.remove(nick);
        if (ip != null) {
            if (suspiciousQuota.containsKey(ip)) {
                int count = suspiciousQuota.get(ip);
                if (count <= 1) {
                    suspiciousQuota.remove(ip);
                } else {
                    suspiciousQuota.put(ip, count - 1);
                }
            }
        }
    }
    public void writeBanToFile(String ipToBan) {
        try {
            Path banFile = dataD.resolve("banlist.txt");
            Files.write(
                    banFile,
                    Collections.singletonList(ipToBan),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            logger.error("[LoDosCk] IO exception writing ban data signature to disk tables!", e);
        }
    }
    private boolean InBanList(String ip) {
        if (ip == null) return false;
        return ipBanList.contains(ip.trim());
    }
    public void SendIPs2BanListServer() {
        if (banQueue.isEmpty()) return;
        Component disconnectMessage = Component.text(banReason != null ? banReason : "§c[LoDosCk] Banned");
        List<String> toProcess = new ArrayList<>(banQueue);
        banQueue.clear();
        for (Object bannedIp : toProcess) {
            writeBanToFile(bannedIp.toString());
            for (Player onlinePlayer : server.getAllPlayers()) {
                String onlineIp = onlinePlayer.getRemoteAddress().getAddress().getHostAddress();
                if (bannedIp.equals(onlineIp)) {
                    onlinePlayer.disconnect(disconnectMessage);
                }
            }
        }
    }
}