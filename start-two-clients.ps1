# Start the distributed file sync system with two clients

Write-Host "Starting File Sync System with two clients..." -ForegroundColor Green

# Start all services
docker-compose up -d

Write-Host "Waiting for services to be ready..." -ForegroundColor Yellow
Start-Sleep 30

Write-Host "Services started:" -ForegroundColor Green
Write-Host "- Server: http://localhost:8080" -ForegroundColor Cyan
Write-Host "- Client 1: Container filesync-client1 (Port 8081)" -ForegroundColor Cyan
Write-Host "- Client 2: Container filesync-client2 (Port 8082)" -ForegroundColor Cyan
Write-Host "- PostgreSQL: localhost:5432" -ForegroundColor Cyan
Write-Host "- Redis: localhost:6379" -ForegroundColor Cyan
Write-Host "- RabbitMQ Management: http://localhost:15672" -ForegroundColor Cyan

Write-Host ""
Write-Host "To view logs:" -ForegroundColor Yellow
Write-Host "docker logs filesync-server" -ForegroundColor White
Write-Host "docker logs filesync-client1" -ForegroundColor White
Write-Host "docker logs filesync-client2" -ForegroundColor White

Write-Host ""
Write-Host "To access client sync directories:" -ForegroundColor Yellow
Write-Host "Client 1: .\client\sync\" -ForegroundColor White
Write-Host "Client 2: .\client2\sync\" -ForegroundColor White

Write-Host ""
Write-Host "To simulate device sync:" -ForegroundColor Yellow
Write-Host "1. Add files to .\client\sync\ and watch them sync to .\client2\sync\" -ForegroundColor White
Write-Host "2. Add files to .\client2\sync\ and watch them sync to .\client\sync\" -ForegroundColor White

Write-Host ""
Write-Host "Press any key to open the sync directories..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")

# Open sync directories
if (Test-Path ".\client\sync") {
    Invoke-Item ".\client\sync"
}
if (Test-Path ".\client2\sync") {
    Invoke-Item ".\client2\sync"
}
