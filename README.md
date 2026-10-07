# MineNorth Entreprises

Mod Forge 1.20.1 pour MineNorth RP. Dépend de `minenorth_eurobank`.

## Commandes (OP / console uniquement)
- `/entreprise <joueur>` : ouvre le guichet des entreprises chez ce joueur. À faire exécuter par le PNJ.
- `/entrepriseadmin` : interface de gestion OP (liste, création, validation, modification, dissolution, employés, grades).
- `/entreprisereload` : recharge `config/minenorth_entreprises.json`.

Les joueurs n'ont aucune commande : sans passage par le PNJ, le serveur refuse toutes leurs actions.

## Configuration (`config/minenorth_entreprises.json`)
- `frais_creation_euros` : frais payés par carte à la création (0 = gratuit).
- `validation_op` : si `true`, l'entreprise reste en attente jusqu'à validation par un OP.
- `activites_bloquees`, `noms_bloques` : mots interdits, ex. `["casino", "armes"]`.
- `salaire_intervalle_minutes`, `salaire_max_euros`, `max_employes`, `max_grades`.

## Salaires
Versés à chaque intervalle aux employés connectés, prélevés sur le compte bancaire du patron.

## Tablette d'entreprise
Objet `minenorthentreprises:tablette_entreprise`, remis au PDG quand son entreprise devient active
(ou via le bouton « Obtenir la tablette » au guichet). Clic droit : gestion des employés, grades et salaires.
Seul le PDG peut l'utiliser. Il ne peut pas changer le nom ni l'activité, et ne peut que **demander** la dissolution :
un OP l'accepte ou la refuse dans `/entrepriseadmin`.
