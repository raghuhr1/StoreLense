#!/usr/bin/env pwsh
# Start all StoreLense backend services as native Java processes
# Prerequisites: PostgreSQL on 5432, Redis on 6379, Kafka on 9092

param(
    [string]$JavaHome  = "C:\Users\Admin\AppData\Local\jdk-21\jdk-21.0.11+10",
    [string]$BackendDir = "D:\StoreLense\backend",
    [string]$LogDir     = "C:\Users\Admin\AppData\Local\Temp\storelense-logs"
)

$env:JAVA_HOME = $JavaHome
New-Item -ItemType Directory -Force $LogDir | Out-Null

$java = "$JavaHome\bin\java.exe"

$services = @(
    @{ name="store-service";           port=8082; jar="store-service/target/storelense-store-service-*.jar"           }
    @{ name="product-service";         port=8083; jar="product-service/target/storelense-product-service-*.jar"         }
    @{ name="inventory-service";       port=8084; jar="inventory-service/target/storelense-inventory-service-*.jar"       }
    @{ name="soh-service";             port=8085; jar="soh-service/target/storelense-soh-service-*.jar"             }
    @{ name="refill-service";          port=8086; jar="refill-service/target/storelense-refill-service-*.jar"          }
    @{ name="rfid-ingest-service";     port=8087; jar="rfid-ingest-service/target/storelense-rfid-ingest-service-*.jar"     }
    @{ name="rfid-processing-service"; port=8088; jar="rfid-processing-service/target/storelense-rfid-processing-service-*.jar" }
    @{ name="reporting-service";       port=8089; jar="reporting-service/target/storelense-reporting-service-*.jar"       }
    @{ name="erp-integration-service"; port=8090; jar="erp-integration-service/target/storelense-erp-integration-service-*.jar" }
    @{ name="notification-service";    port=8091; jar="notification-service/target/storelense-notification-service-*.jar"    }
)

$commonArgs = @(
    "-XX:+UseContainerSupport"
    "-XX:MaxRAMPercentage=50.0"
    "--spring.datasource.password=postgres"
    "--spring.datasource.username=postgres"
    "--spring.datasource.url=jdbc:postgresql://localhost:5432/storelense"
    "--spring.kafka.bootstrap-servers=localhost:9092"
)

foreach ($svc in $services) {
    $jarGlob = Join-Path $BackendDir $svc.jar
    $jar     = (Resolve-Path $jarGlob -ErrorAction SilentlyContinue)[0]
    if (-not $jar) {
        Write-Host "[SKIP] $($svc.name) — JAR not found" -ForegroundColor Yellow
        continue
    }

    $log = "$LogDir\$($svc.name).log"
    $argList = @("-jar", $jar.Path) + $commonArgs
    Start-Process -FilePath $java `
        -ArgumentList $argList `
        -RedirectStandardOutput $log `
        -RedirectStandardError  "$log.err" `
        -WindowStyle Hidden `
        -ErrorAction Stop
    Write-Host "[START] $($svc.name) → port $($svc.port)" -ForegroundColor Green
}

Write-Host "`nAll services starting. Check logs in: $LogDir"
Write-Host "Run '.\test.ps1' after ~60s to verify everything is healthy."
