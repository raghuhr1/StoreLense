package com.storelense.erp.service;

import com.storelense.erp.config.ErpImportProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storelense.erp.import.local-enabled", havingValue = "true")
public class LocalFolderWatcher {

    private final ErpImportService   importService;
    private final ErpImportProperties importProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void startWatching() throws IOException {
        Path folder = Path.of(importProperties.localFolder());
        if (!Files.isDirectory(folder)) {
            Files.createDirectories(folder);
        }

        WatchService watchService = FileSystems.getDefault().newWatchService();
        folder.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);

        log.info("LocalFolderWatcher: watching {}", folder);

        Thread thread = Thread.ofVirtual().name("erp-folder-watcher").start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key;
                try {
                    key = watchService.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                        @SuppressWarnings("unchecked")
                        Path created = folder.resolve(((WatchEvent<Path>) event).context());
                        if (Files.isRegularFile(created)) {
                            log.info("LocalFolderWatcher: detected {}", created);
                            importService.processFile(created);
                        }
                    }
                }
                if (!key.reset()) {
                    log.warn("LocalFolderWatcher: watch key invalid, stopping");
                    break;
                }
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(thread::interrupt));
    }
}
