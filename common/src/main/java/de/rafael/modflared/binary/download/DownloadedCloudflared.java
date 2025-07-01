package de.rafael.modflared.binary.download;

//------------------------------
//
// This class was developed by Rafael K.
// On 10/31/2022 at 11:17 PM
// In the project cloudflared
//
//------------------------------

import de.rafael.modflared.Modflared;
import de.rafael.modflared.binary.Cloudflared;
import de.rafael.modflared.github.GithubAPI;
import de.rafael.modflared.tunnel.RunningTunnel;
import de.rafael.modflared.tunnel.manager.TunnelManager;
import net.minecraft.util.Pair;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.Platform;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

public class DownloadedCloudflared extends Cloudflared {

    private final CloudflaredDownload download;

    private static final File VERSION_FILE = new File(TunnelManager.DATA_FOLDER, "version.json");

    private static final String GITHUB_DOWNLOAD_ENDPOINT = "https://github.com/cloudflare/cloudflared/releases/download/";

    public DownloadedCloudflared(CloudflaredDownload download, String version) {
        super(version);
        this.download = download;
    }

    public static CompletableFuture<Cloudflared> tryCreate() {
        if(VERSION_FILE.exists()) {
            try {
                var version = Modflared.GSON.fromJson(new InputStreamReader(new FileInputStream(VERSION_FILE)), DownloadedCloudflared.class);
                if(version != null) return CompletableFuture.completedFuture(version);
            } catch (Throwable throwable) {
                Modflared.LOGGER.error("Failed to load existing version file creating new one...", throwable);
            }
        }
        return GithubAPI.requestLatestVersion().thenApply(latestVersion -> new DownloadedCloudflared(CloudflaredDownload.find(), latestVersion));
    }

    @Override
    public CompletableFuture<Void> prepare() {
        if(isInstalled()) {
            CompletableFuture<Void> completableFuture = new CompletableFuture<>();
            isUptoDate().whenComplete((pair, throwable) -> {
                if (throwable != null) {
                    Modflared.LOGGER.error("Failed to check for updates", throwable);
                    TunnelManager.displayErrorToast();
                    completableFuture.complete(null);
                } else {
                    if(!pair.getLeft()) {
                        Modflared.LOGGER.info("Update detected updating...");
                        version = pair.getRight();
                        downloadAndSaveInfo().whenComplete((unused, throwable1) -> {
                            if (throwable1 != null) Modflared.LOGGER.error("Failed to download update", throwable1);
                            TunnelManager.displayErrorToast();
                            completableFuture.complete(null);
                        });
                    } else {
                        Modflared.LOGGER.info("Cloudflared has no updates :)");
                        completableFuture.complete(null);
                    }
                }
            });
            return completableFuture;
        } else {
            return downloadAndSaveInfo();
        }
    }

    @Override
    public String[] buildCommand(RunningTunnel.@NotNull Access access) {
        var command = access.command(createBinaryRef().getName(), true);
        Modflared.LOGGER.info(Arrays.toString(command).replace(",",""));
        if (Platform.get() == Platform.WINDOWS) {
            command[0] = "\"" + TunnelManager.DATA_FOLDER.getAbsolutePath() + "\\" + command[0] + "\"";
        }
        return command;
    }

    private CompletableFuture<Void> downloadAndSaveInfo() {
        return downloadFile().thenAccept(unused -> {
            try {
                save();
            } catch (Throwable throwable) {
                Modflared.LOGGER.error("Failed to save current installed version", throwable);
                TunnelManager.displayErrorToast();
            }
        });
    }

    public boolean isInstalled() {
        return createBinaryRef().exists() && VERSION_FILE.exists();
    }

    @Contract(" -> new")
    public @NotNull File createBinaryRef() {
        return new File(TunnelManager.DATA_FOLDER, download.fileName());
    }

    public CompletableFuture<Pair<Boolean, String>> isUptoDate() {
        return GithubAPI.requestLatestVersion().thenApply(latestVersion -> new Pair<>(latestVersion.equals(version), latestVersion));
    }

    public @NotNull CompletableFuture<Void> downloadFile() {
        return GithubAPI.requestFileHash(download.downloadFile()).thenAcceptAsync(fileHash -> {
            try {
                for (int i = 0; i < 5; i++) {
                    Modflared.LOGGER.info("Downloading cloudflared version {} from github. Attempt: {}", version, i + 1);
                    File file = syncDownloadFile();

                    // Check if file is corrupt
                    if(fileHash.compareTo(file)) {
                        Modflared.LOGGER.info("Preparing cloudflared binary...");
                        prepareFile(file);
                        Modflared.LOGGER.info("Download finished of cloudflared version {}!", version);
                        return;
                    } else {
                        file.delete();
                    }
                }
            } catch (InterruptedException exception) {
                throw new IllegalStateException("Error while unpacking MacOS cloudflared download", exception);
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to download cloudflared binary", exception);
            }
            throw new IllegalStateException("Modflared failed 5 times to download cloudflared from github. Please check your internet connection");
        }, Modflared.EXECUTOR);
    }

    private void prepareFile(File file) throws IOException, InterruptedException {
        switch (Platform.get()) {
            case MACOSX:
                new ProcessBuilder("tar", "-xzf", file.getName()).directory(file.getParentFile()).start().waitFor();
                new ProcessBuilder("mv", "cloudflared", file.getName()).directory(file.getParentFile()).start().waitFor();
                //Fallthrough
            case LINUX:
                new ProcessBuilder("chmod", "+x", file.getName()).directory(file.getParentFile()).start();
                break;
            default:
                break;
        }
    }

    private @NotNull File syncDownloadFile() throws IOException, InterruptedException {
        File output = new File(TunnelManager.DATA_FOLDER, download.fileName());
        if(!output.getParentFile().exists()) output.getParentFile().mkdirs();
        if(!output.exists()) output.createNewFile();
        try (BufferedInputStream in = new BufferedInputStream(URI.create(GITHUB_DOWNLOAD_ENDPOINT + version + "/" + download.downloadFile()).toURL().openStream()); BufferedOutputStream fileOutputStream = new BufferedOutputStream(new FileOutputStream(output))) {
            byte[] dataBuffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
            fileOutputStream.flush();
        }

        return output;
    }

    private void save() throws IOException {
        Files.writeString(VERSION_FILE.toPath(), Modflared.GSON.toJson(this), StandardCharsets.UTF_8);
    }

}
