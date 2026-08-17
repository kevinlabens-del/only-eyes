ONLY EYES — V2.8

Version prête à héberger en HTTPS.

Nouveautés V2.8 :
- choix du rendu caméra : COULEUR ou NOIR & BLANC ;
- glitch beaucoup plus agressif aux intensités élevées ;
- nouveau mode GLITCH EXTRÊME ;
- déchirures horizontales plus nombreuses et plus larges ;
- séparation chromatique rouge/bleu (RGB split) ;
- pixelisation renforcée et blocs de signal corrompu ;
- flashs rouges/bleus aléatoires et micro-points parasites ;
- fréquence maximale nettement plus rapide ;
- bandes d'informations retravaillées avec fond rouge/bleu et points aléatoires ;
- migration automatique des réglages V2.6 quand disponible ;
- cache Service Worker V2.8.

Conservé depuis V2.6 :
- MediaPipe Face Landmarker pour suivre précisément les deux yeux ;
- stabilisation et vitesse de suivi réglables ;
- recentrage automatique du crop ;
- chaîne micro Web Audio ;
- transformation vocale ;
- MediaRecorder vidéo + audio ;
- grain, scanlines et indicateurs CAM / MIC / EYES.

IMPORTANT :
- HTTPS obligatoire pour caméra + micro ;
- MediaPipe charge ses dépendances en ligne au premier lancement ;
- si MediaPipe ne charge pas, le cadrage de secours reste disponible.

Déploiement : envoyer tous les fichiers et dossiers à la racine de l'hébergement HTTPS.