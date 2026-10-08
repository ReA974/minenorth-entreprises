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
Versés à chaque intervalle aux employés connectés qui ont un compte bancaire, prélevés sur le **compte entreprise**
(virement de compte à compte, pas du compte perso du patron). Si le compte est vide ou insuffisant, la paie n'est pas
versée : l'employé et le patron sont prévenus, et une alerte « redressement judiciaire » est envoyée au patron et aux
gérants connectés (une fois par cycle et par entreprise). Aucune sanction automatique.

## Compte entreprise
- Chaque entreprise a un compte EuroBank ouvert dès sa création (même en attente de validation). Il est fermé quand
  l'entreprise est refusée ou dissoute.
- Accès réservé au PDG et aux membres dont le grade a le droit de gestion (« manage », ex. Co-gérant). Le serveur refuse
  les autres et exige une entreprise active.
- **Dépôt** depuis son compte perso, **virement** vers son compte perso (celui de l'acteur, même pour un gérant).
  Maximum 10 M€ par opération.
- **Carte entreprise** : bouton de l'onglet TRANSACTIONS. À tenir **en main** pour payer, sinon la carte perso est utilisée.
  Une carte volée ou d'un titulaire qui n'est plus signataire est refusée.
- Historique : 200 transactions conservées par compte (paies, dépôts, virements, paiements, corrections admin).
- Dissolution (ou refus) : le solde va au patron, ou au Treasury s'il n'a pas de compte. Wipe du patron : solde au
  Treasury (source `entreprises:wipe`).
- Les frais de création restent payés par la carte perso.

## Tablette d'entreprise
Objet `minenorthentreprises:tablette_entreprise`, remis au PDG quand son entreprise devient active
(ou via le bouton « Obtenir la tablette » au guichet). Clic droit : gestion des employés, grades et salaires.
L'objet ne fonctionne que pour le PDG ; les co-gérants (grade avec droit de gestion) accèdent au même écran par le guichet
(`/entreprise <joueur>`). Le PDG ne peut pas changer le nom ni l'activité, et ne peut que **demander** la dissolution :
un OP l'accepte ou la refuse dans `/entrepriseadmin`.

Onglet **TRANSACTIONS** (PDG et grades avec droit de gestion, jamais en mode admin) : solde, boutons Déposer,
Virer vers mon compte et Obtenir ma carte entreprise, puis historique paginé.

## API
`EntrepriseApi.accountOf(server, companyId)` renvoie l'UUID du compte entreprise (ou `null`). Les autres mods s'en
servent avec `MineNorth.bank().payFromAccount(...)` pour payer depuis ce compte (versé au Treasury avec une source) et
`MineNorth.bank().transfer(...)` pour créditer ou débiter de compte à compte.
