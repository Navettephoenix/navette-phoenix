# Déploie UNIQUEMENT les règles Firestore V20 du projet de réservation.
# Exécuter depuis PowerShell sur la machine autorisée, après vérification du projet.
$ErrorActionPreference = 'Stop'
$project = 'navette-phoenix'
$firebaseJson = Join-Path $PSScriptRoot '..\firebase\firebase.json'
if (-not (Test-Path $firebaseJson)) {
    throw "firebase.json introuvable : $firebaseJson"
}
$firebase = Get-Command firebase.cmd -ErrorAction SilentlyContinue
if (-not $firebase) {
    throw "Firebase CLI introuvable. Installez firebase-tools avec npm.cmd install -g firebase-tools."
}
Push-Location (Split-Path $firebaseJson)
try {
    Write-Host "Deploiement des regles Firestore UNIQUEMENT sur $project" -ForegroundColor Cyan
    & $firebase.Source deploy --only firestore:rules --project $project
    if ($LASTEXITCODE -ne 0) { throw "Deploiement Firestore refuse ou incomplet : code $LASTEXITCODE." }
    Write-Host "Regles Firestore deployeees : verification par une seule reservation de test requise." -ForegroundColor Green
}
finally {
    Pop-Location
}
