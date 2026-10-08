# Compte entreprise EuroBank : design

Date : 2026-10-08. Branche : `feat/compte-entreprise-bank`.
Dépôts touchés : `minenorth-entreprises` (principal), `minenorth-api` et `minenorth-eurobank` (modifications autorisées par le propriétaire).

## 1. Objectif

Chaque entreprise possède un **vrai compte EuroBank**, ouvert dès la création de l'entreprise (donc dès le statut PENDING).

- Les **paies** des employés sont prélevées sur ce compte.
- Le **patron et les grades avec le droit de gestion** (`Grade.manage`, ex. Co-gérant) peuvent déposer de l'argent depuis leur compte perso, virer vers leur compte perso, et payer avec une **carte entreprise**.
- Un onglet **TRANSACTIONS** de la tablette entreprise liste tous les mouvements du compte : paies, dépôts, virements, paiements, recettes, corrections admin.
- Si le compte est vide au moment de la paie, la paie n'est pas versée et le patron et les gérants connectés sont alertés que le compte est à 0 et que l'entreprise risque le redressement judiciaire. Aucune sanction automatique.

Succès : une entreprise créée a un compte, les paies passent par la banque, un gérant paie un achat en boutique avec la carte entreprise tenue en main, et l'historique complet est lisible dans la tablette.

## 2. Hors périmètre

- Plafond de dépense par carte.
- Relevé de transactions pour les comptes joueurs (ATM).
- Prêts pour les comptes entreprise (interdits).
- Sanction automatique du redressement judiciaire.

## 3. Règles d'accès

| Action | Qui |
|---|---|
| Voir l'onglet TRANSACTIONS, déposer, virer, obtenir une carte entreprise, payer avec la carte | PDG et membres dont le grade a `manage` |
| Autres membres | aucun accès, le serveur refuse aussi les paquets |

Tout est validé côté serveur : droits, montant positif, solde suffisant, entreprise ACTIVE (sauf ouverture du compte, qui se fait dès PENDING).

## 4. API (`minenorth-api`, `BankService`)

Ajouts en méthodes `default`, pour ne casser aucun mod déjà compilé. `BankService.NONE` renvoie `UNAVAILABLE` ou `false`.

- `openBusinessAccount(s, accountId, label)` : ouvre et marque un compte entreprise.
- `renameAccount(s, accountId, label)`.
- `closeAccount(s, accountId)` : ferme le compte, solde à 0 exigé.
- `setSigners(s, accountId, Set<UUID>)` : joueurs autorisés à payer avec la carte entreprise.
- `payFromAccount(s, accountId, cents, source, actor)` : débit sans carte, versé au Treasury avec la source, transaction enregistrée.
- `history(s, accountId, limit)` : liste de `BankTx` (nouveau record de l'API : date, type, montant, solde après, libellé, auteur ; pas de contrepartie), du plus récent au plus ancien.

`transfer` existant sert aux dépôts, virements, paies et recettes.

## 5. Banque (`minenorth-eurobank`)

- **`BankData`** : ensemble `businessAccounts` et map `signers` (accountId vers UUID), sauvegardés en NBT. Historique par compte entreprise, borné (200 par défaut, configurable). Méthode `record(accountId, tx)`.
- Les comptes entreprise sont **exclus** de `findByName` (recherche de joueurs), de l'admin, de l'ATM et des prêts.
- **`BankProvider`** : implémente les nouvelles méthodes. `transfer`, `debit` et `refund` appellent `record` quand l'un des comptes est un compte entreprise. Le motif de la transaction est dérivé du `source` fourni.
- **`BankApi.charge`** : si le joueur tient en **main principale** une `BusinessCardItem` à son nom (titulaire = lui) dont le compte l'inclut dans ses signataires, le débit se fait sur le compte entreprise, avec `record`. Sinon, comportement actuel (carte perso).
- **`BusinessCardItem`** : NBT `Account` (UUID), `Holder` (UUID), `CompanyName`. Carte volée refusée (`FOREIGN_CARD`). Un titulaire retiré des signataires est refusé.
- Les nouveaux codes de refus restent dans `PayResult` existant.

## 6. Mod entreprises

- **`Company`** : nouveau champ `accountId` (UUID), sérialisé en NBT. Aucun solde n'est stocké. Au chargement, une entreprise existante sans `accountId` en reçoit un et son compte est ouvert à 0.
- **Création** : `create()` appelle `openBusinessAccount` juste après la création de la `Company`. Refus (`REFUSE`) ou dissolution : transfert du solde au patron, puis `closeAccount`.
- **Renommage** (`EDIT`) : `renameAccount`.
- **Signataires** : `setSigners` est rappelé à chaque changement de membres, de grades ou de droit `manage`, de patron, et au chargement du serveur.
- **Paies** (`payroll`) : `transfer(compte entreprise, employé, salaire)` pour chaque employé connecté avec un compte. Si le solde est insuffisant : paie sautée, message à l'employé, et alerte « compte à 0, risque de redressement judiciaire » au patron et aux gérants connectés, une fois par cycle et par entreprise. Ces transferts sont enregistrés comme `SALARY`.
- **Dépôt** : `transfer(patron, compte entreprise, montant)`, type `DEPOSIT`. **Virement** : `transfer(compte entreprise, patron, montant)`, type `WITHDRAW`. L'acteur est le joueur, qui peut être un gérant ; le « patron » du virement est toujours le compte perso de l'acteur.
- **Dissolution** : solde restant transféré au patron, puis `closeAccount`. **Wipe du patron** (`EntrepriseWipe`) : solde versé au Treasury (`charge` côté banque avec la source `entreprises:wipe`), puis `closeAccount`.
- **`EntrepriseApi`** : ajout de `accountOf(server, companyId)` pour que les autres mods paient ou créditent par `payFromAccount` et `transfer`.
- **Frais de création** : inchangés (carte perso, `bank().charge`).

## 7. Tablette

- Nouvel onglet **TRANSACTIONS**, visible uniquement avec le droit de gestion.
- En haut : solde, boutons **Déposer**, **Virer vers mon compte**, **Obtenir ma carte entreprise** (une carte par titulaire, regénérable si perdue).
- Dessous : transactions paginées (date, libellé, montant coloré, solde après, auteur en identité RP via `MineNorth.displayName`).
- `StatePacket` : solde, indicateur d'accès, 50 dernières transactions. `ActionPacket` : nouveaux codes `DEPOSIT=18`, `WITHDRAW=19`, `GET_BUSINESS_CARD=20` ; le montant est passé en texte (euros) dans `ActionPacket.a`, interprété par l'existant `EntrepriseService.euros(String)` ; pas dans `n`.
- Charte `MineNorthStyle` et `MineNorthButton`.

## 8. Cohérence monétaire

- Dépôts, virements, paies et recettes sont de vrais transferts de compte à compte : rien ne passe par le Treasury.
- Un achat avec la carte entreprise suit la règle de la banque : il part au Treasury avec sa source.
- Seul le wipe du patron verse un solde au Treasury.

## 9. Gestion d'erreurs

- Banque absente (`BankService.NONE`) : l'entreprise n'est pas créée et l'utilisateur voit « service bancaire indisponible » ; les paies sont sautées avec message.
- Montant invalide, droits insuffisants, solde insuffisant : message d'erreur dans le `StatePacket`, rien n'est débité.
- Échec de `closeAccount` : la dissolution (ou le wipe) n'est pas bloquée ; un avertissement est journalisé avec le nom, l'id, l'`accountId` et le solde restant, que l'admin pourra récupérer sur le compte.

## 10. Tests

Le dépôt n'a aucun test. Les cas à vérifier à la main en jeu, ou en tests unitaires de `CompanyAccounts` si une couche testable est extraite : création, paie avec solde suffisant et insuffisant, alerte unique par cycle, dépôt et virement par un gérant, refus pour un simple employé, achat avec la carte en main et sans, carte volée, gérant rétrogradé, dissolution, wipe, renommage, migration d'une entreprise existante.

## 11. Ordre de réalisation prévu

1. API : `BankService`, `BankTx`.
2. Banque : `BankData`, `BankProvider`, `BankApi.charge`, `BusinessCardItem`.
3. Mod entreprises : `Company.accountId`, création, paies, dépôt et virement, dissolution, wipe.
4. Tablette et paquets.
