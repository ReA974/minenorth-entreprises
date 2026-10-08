# Factures, ATM entreprise, virements tablette : plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** (A) l'ATM refuse la carte entreprise ; (B) l'ATM propose « Virement à un joueur » ou « à une entreprise » (liste) ; (C) système de facture entreprise → client avec signature et prélèvement différé ; (D) transactions visibles dans `/entrepriseadmin` ; (E) la tablette permet de virer du compte entreprise vers un joueur (nom RP).

**Architecture:** extension du lot « compte entreprise » déjà livré (mêmes trois dépôts, mêmes branches). La banque ne connaît toujours pas le mod entreprises : le mod entreprises marque les comptes « listés » via l'API. Les factures vivent dans le mod entreprises (`EntrepriseData`) et se règlent par `BankService.transfer`.

**Tech Stack:** Java 17, Forge 1.20.1, ForgeGradle, JUnit 5 (EuroBank seulement).

**Design:** validé en conversation le 2026-10-08 (aucune spec écrite à la demande de l'utilisateur) ; le plan ci-dessous en est la référence. Contexte du lot précédent : `docs/superpowers/specs/2026-10-08-compte-entreprise-design.md` et `docs/superpowers/plans/2026-10-08-compte-entreprise.md`.

## Global Constraints

- Branches : `minenorth-api` et `minenorth-eurobank` sur `feat/entreprise` ; `minenorth-entreprises` sur `feat/compte-entreprise-bank`. Ne jamais changer de branche. Les trois dépôts ont des fichiers `.gradle/`, `.idea/`, `build/` suivis et modifiés ; l'utilisateur a aussi supprimé `.idea/modules.xml` et modifié `.idea/gradle.xml` dans EuroBank : **jamais** `git add -A` ni `git commit -a`, chemins explicites uniquement, et vérifier `git show --stat HEAD`.
- Messages de commit terminés par `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
- Montants en centimes (`long`) ; plafond par opération `1_000_000_000L` (10 M€).
- Nouvelles méthodes de `BankService` : `default`, jamais abstraites. Un seul ajout d'API dans ce lot : `setAccountListed`. Le jar API garde son nom (`version = '1.0.0'` dans `build.gradle`) ; ne pas toucher aux versions.
- Noms affichés : nom RP (`MineNorth.displayName(...)` ou `BankData.name(...)`), jamais le pseudo.
- Droits tablette (transactions, virements, factures) : patron ou grade `manage == true`, vérifiés côté serveur ; l'entreprise est toujours celle du joueur (`companyOf`), jamais un id fourni par le client, sauf mode admin (`p.hasPermissions(2)`).
- Protocole réseau du mod entreprises : `"2"` passe à `"3"` (une seule fois, tâche 6). ATM EuroBank : son protocole éventuel doit être bumpé si un paquet change de format (vérifier le canal dans `Network.java`).
- Config `minenorth_entreprises.json` (nouveaux champs) : `facture_distance_blocs` = 10, `facture_signature_secondes` = 300, `facture_relance_minutes` = 10, `facture_expiration_jours` = 0 (0 = jamais).
- Factures : description 64 caractères max ; montant > 0 et ≤ 10 M€ ; au plus 3 factures `AWAITING_SIGNATURE` par client (toutes entreprises) ; pas de facture à soi-même ; le client doit être en ligne et à moins de `facture_distance_blocs` blocs (même dimension) à la création.
- Les jars se consomment ainsi : API d'abord (`../minenorth-api/build/libs/minenorth_api-1.0.0.jar`), puis EuroBank et entreprises. Construire dans cet ordre.
- Pas de nouvelle infrastructure de test dans `minenorth-entreprises` (aucun test dans ce dépôt) ; JUnit existe dans `minenorth-eurobank` (étendre `BankDataBusinessTest` pour la logique pure).

## Review Focus

1. **Facture signée deux fois ou prélevée deux fois** (double clic sur Signer, relance pendant la signature, deux ticks) : une facture ne se paie qu'une fois. Test : tâche 8, vérification et relecture.
2. **Client déconnecté, wipé ou entreprise dissoute pendant qu'une facture est `AWAITING_SIGNATURE` ou `FAILED`** : annulation propre, jamais de prélèvement vers un compte fermé. Test : tâche 8.
3. **Montant ou texte hostile** (description très longue ou avec `§`, montant `abc`, `-5`, `1e300`, UUID client invalide ou d'un autre joueur) : refus, aucun mouvement. Test : tâches 6, 8 et 9.
4. **Un client qui n'est pas le destinataire de la facture tente de la signer** (id deviné) : refus. Test : tâche 9.
5. **Virement ATM vers une entreprise non listée ou inexistante** (UUID forgé, compte joueur, entreprise dissoute) : refus sans mouvement. Test : tâche 4.

---

## File Structure

| Fichier | Rôle |
|---|---|
| `minenorth-api/.../BankService.java` (modifier) | `setAccountListed` (`default`) |
| `minenorth-eurobank/.../BankData.java`, `api/BankProvider.java` (modifier) | Comptes « listés », liste triée, implémentation |
| `minenorth-eurobank/.../blocks/AtmBlock.java`, `packet/ActionPacket.java`, `packet/StatePacket.java`, `client/AtmScreen.java`, `items/BusinessCardItem.java` (modifier) | A et B |
| `minenorth-entreprises/.../CompanyAccounts.java`, `EntrepriseService.java` (modifier) | Listage, virement joueur |
| `minenorth-entreprises/.../InvoiceService.java` (créer) | Logique des factures (création, signature, refus, prélèvement différé, annulation) |
| `minenorth-entreprises/.../data/EntrepriseData.java`, `config/EntrepriseConfig.java` (modifier) | `Invoice`, persistance NBT, config |
| `minenorth-entreprises/.../network/ModNetwork.java`, `client/EntrepriseScreen.java`, `client/InvoiceScreen.java` (créer), `client/ClientNetworkHandler.java` (modifier) | Réseau et écrans |

Chemins complets : `src/main/java/fr/minenorth/api/`, `src/main/java/com/minenorth_eurobank/`, `src/main/java/fr/minenorth/entreprises/`.

---

### Task 1: API : `setAccountListed`

**Files:**
- Modify: `minenorth-api/src/main/java/fr/minenorth/api/BankService.java`

**Interfaces:**
- Produces : `default void setAccountListed(MinecraftServer s, UUID accountId, boolean listed)` (ne fait rien par défaut) ; javadoc : « Rend un compte entreprise visible (ou non) dans la liste des entreprises de l'ATM. »

- [ ] **Step 1: Ajouter la méthode `default`** avec la javadoc ci-dessus ; `NONE` hérite du comportement par défaut.
- [ ] **Step 2: Compiler.** `.\gradlew.bat build` dans `minenorth-api`. Attendu : `BUILD SUCCESSFUL`, jar `build/libs/minenorth_api-1.0.0.jar` mis à jour.
- [ ] **Step 3: Commit** (dans `minenorth-api`) : `git add src/main/java/fr/minenorth/api/BankService.java` ; message `feat(api): setAccountListed pour la liste des entreprises de l'ATM`.

---

### Task 2: Banque : comptes listés

**Files:**
- Modify: `minenorth-eurobank/src/main/java/com/minenorth_eurobank/BankData.java`, `api/BankProvider.java`
- Test: `minenorth-eurobank/src/test/java/com/minenorth_eurobank/BankDataBusinessTest.java` (étendre)

**Interfaces:**
- Consumes : `BankService.setAccountListed` (tâche 1) ; `BankData.isBusiness`, `name`.
- Produces :
  - Dans `BankData` : `boolean isListed(UUID id)`, `void setListed(UUID id, boolean on)` (sans effet si `!isBusiness`), `List<Map.Entry<UUID, String>> listedBusinesses()` (triée par nom insensible à la casse, nom = libellé du compte). Persistance NBT (liste `listed`) ; `closeBusiness` retire aussi le marquage.
  - `BankProvider.setAccountListed(s, accountId, listed)` délègue à `BankData.setListed`.

- [ ] **Step 1: Écrire le test** (`BankDataBusinessTest`) : `listedBusinessesIsSortedAndOnlyListed` (3 comptes entreprise, 2 listés, l'ordre est alphabétique insensible à la casse), `listingSurvivesSaveLoad`, `closeUnlists`.
- [ ] **Step 2: Lancer, constater l'échec** : `.\gradlew.bat test --tests "*BankDataBusinessTest*"`.
- [ ] **Step 3: Implémenter** dans `BankData` et `BankProvider`.
- [ ] **Step 4: Lancer, constater le succès** : même commande, puis `.\gradlew.bat build` (les tests existants restent verts).
- [ ] **Step 5: Commit** (chemins explicites : `BankData.java`, `BankProvider.java`, le test) ; message `feat(bank): comptes entreprise listes pour l'ATM`.

---

### Task 3: Banque : l'ATM refuse la carte entreprise (A)

**Files:**
- Modify: `minenorth-eurobank/.../blocks/AtmBlock.java`, `packet/ActionPacket.java`, `items/BusinessCardItem.java` (info-bulle)

**Interfaces:**
- Consumes : `BusinessCardItem` (helper existant qui détecte une carte entreprise en main principale, voir `holdsCard`/`activeAccount`).
- Produces : constante `BusinessCardItem.ATM_REFUSAL = "Cette carte n'est pas reconnue par le distributeur."`.

- [ ] **Step 1: `AtmBlock.use`** : si le joueur (serveur) tient une carte entreprise en main principale, ne pas ouvrir l'ATM ; envoyer `ATM_REFUSAL` dans la barre d'action (`displayClientMessage(Component, true)`), retourner `InteractionResult.sidedSuccess(level.isClientSide)`.
- [ ] **Step 2: `ActionPacket.handle`** : même refus (message système dans `Network.sendState` ou barre d'action) si la carte entreprise est en main principale au moment de l'action, avant tout traitement.
- [ ] **Step 3: Info-bulle** : ajouter la ligne « Achats uniquement : non reconnue aux distributeurs. » à `BusinessCardItem.appendHoverText`.
- [ ] **Step 4: Compiler** : `.\gradlew.bat build`. Attendu : `BUILD SUCCESSFUL`. Vérification en jeu notée pour la tâche 11 (carte en main : ATM fermé et message ; sans carte entreprise : ATM normal).
- [ ] **Step 5: Commit** (chemins explicites) ; message `feat(bank): l'ATM refuse la carte entreprise`.

---

### Task 4: Banque : virement à une entreprise par liste (B)

**Files:**
- Modify: `minenorth-eurobank/.../packet/ActionPacket.java` (enum `Action.TRANSFER_COMPANY`, cible 64 caractères), `packet/StatePacket.java` (liste des entreprises listées), `client/AtmScreen.java` (pages), `Network.java` (version du canal si nécessaire)

**Interfaces:**
- Consumes : `BankData.listedBusinesses()`, `isListed`, `BankProvider.transfer` 7 arguments (catégorie `BankTx.INCOME`).
- Produces :
  - `ActionPacket.Action.TRANSFER_COMPANY` : `amount` = centimes, `target` = UUID du compte (texte).
  - `StatePacket` gagne `List<CompanyEntry(UUID id, String name)> companies`, au plus 100 (triées par nom), calculé côté serveur à partir de `listedBusinesses()`.
  - Page ATM `TRANSFER` : deux boutons « Virement à un joueur » (écran actuel) et « Virement à une entreprise » (page `TRANSFER_COMPANY` : liste paginée de 5 noms, précédent et suivant, puis montant et « Envoyer »).

- [ ] **Step 1: Serveur** : le cas `TRANSFER_COMPANY` dans `process` : montant ≤ 0 ou > 1_000_000_000L → « Montant invalide. » ; UUID invalide → « Entreprise introuvable. » ; compte non listé ou inexistant → « Aucune entreprise à ce nom. » ; solde insuffisant → « Solde insuffisant. » ; sinon `transfer(server, joueur, compte, montant, BankTx.INCOME, "Virement de " + nom RP, nom RP)` et message « Virement de X envoyé à <entreprise>. ». Le joueur reçoit les mêmes contrôles de carte que pour `TRANSFER` (sa propre carte requise).
- [ ] **Step 2: `StatePacket`** : ajouter la liste, encode et decode symétriques ; bump du protocole du canal ATM si le canal EuroBank en a un.
- [ ] **Step 3: Écran** : page de choix, page liste (index de page conservé au rafraîchissement, bornes sûres pour liste vide ou plus courte), champ montant, état du bouton Envoyer ; champ pseudo du virement joueur inchangé.
- [ ] **Step 4: Compiler** : `.\gradlew.bat build`. Attendu : `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit** (chemins explicites) ; message `feat(bank): virement ATM vers une entreprise (liste)`.

---

### Task 5: Entreprises : listage des comptes ACTIVE

**Files:**
- Modify: `CompanyAccounts.java`, `EntrepriseService.java`

**Interfaces:**
- Consumes : `BankService.setAccountListed` (tâche 1, jar reconstruit).
- Produces : `CompanyAccounts.syncListing(MinecraftServer s, Company c)` : appelle `setAccountListed(s, c.accountId, c.status == EntrepriseData.ACTIVE)`.

- [ ] **Step 1: Implémenter `syncListing`** et l'appeler : à l'ouverture du compte (statut PENDING → non listé), à la validation (VALIDATE), à la création ACTIVE (`create` sans validation, `adminCreate`), au démarrage du serveur pour chaque entreprise, et après chaque `handle` pour l'entreprise concernée comme `syncSigners`. La dissolution et le refus appellent déjà `close` qui retire le marquage côté banque (tâche 2).
- [ ] **Step 2: Compiler** : API reconstruite puis `.\gradlew.bat build`. Attendu : `BUILD SUCCESSFUL`.
- [ ] **Step 3: Commit** (chemins explicites) ; message `feat(entreprises): liste des entreprises actives pour l'ATM`.

---

### Task 6: Entreprises : virement vers un joueur depuis la tablette (E)

**Files:**
- Modify: `CompanyAccounts.java`, `EntrepriseService.java` (`act`), `network/ModNetwork.java` (code d'action, protocole `"3"`), `client/EntrepriseScreen.java` (onglet TRANSACTIONS)

**Interfaces:**
- Consumes : `canBank`, `check(...)` existant (accès, ACTIVE, plafond), `MineNorth.identity().known(s)`, `MineNorth.displayName(s, uuid)`, `BankService.hasAccount`, `transfer` 7 arguments.
- Produces :
  - `ModNetwork.TRANSFER_PLAYER = 21` ; `ActionPacket` : `a` = montant (texte euros, analysé par `euros(...)`), `b` = nom RP du destinataire, `c` = motif (≤ 64 caractères, nettoyé par `clean`).
  - `CompanyAccounts.payPlayer(ServerPlayer p, Company c, long cents, String rpName, String motif) -> String` (message d'erreur ou `null`).
  - `CompanyAccounts.findAccountByRpName(MinecraftServer s, String rpName) -> UUID` : compare sans tenir compte de la casse le nom RP de chaque joueur de `identity().known(s)` ayant un compte ; `null` sinon.

- [ ] **Step 1: `findAccountByRpName` et `payPlayer`** : refus si `!canBank`, entreprise non ACTIVE, montant ≤ 0 ou > 1_000_000_000L, destinataire introuvable (« Aucun joueur de ce nom. »), destinataire = son propre compte (« Utilisez Virer vers mon compte. ») ; sinon `transfer(s, c.accountId, dest, cents, BankTx.WITHDRAW, "Virement à " + nomRp + (motif vide ? "" : " : " + motif), nom RP de l'acteur)`.
- [ ] **Step 2: `act`** : nouveau cas `TRANSFER_PLAYER`, mode non admin seulement, entreprise = `companyOf(p)`.
- [ ] **Step 3: Écran** : dans l'onglet TRANSACTIONS, champs Destinataire (nom RP) et Motif, bouton **Virer à un joueur** ; réutilise le champ montant existant. Protocole `"3"` dans `ModNetwork`.
- [ ] **Step 4: Compiler** : `.\gradlew.bat build`. Attendu : `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit** ; message `feat(entreprises): virement du compte entreprise vers un joueur`.

---

### Task 7: Entreprises : transactions et soldes dans `/entrepriseadmin` (D)

**Files:**
- Modify: `EntrepriseService.java`, `network/ModNetwork.java`, `client/EntrepriseScreen.java` (mode admin)

**Interfaces:**
- Consumes : `BankService.balance`, `history`, `ModNetwork.TxView`, `CompanyView` (existants).
- Produces :
  - `ModNetwork.VIEW_TX = 22` (admin seulement, `companyId` dans l'`ActionPacket`).
  - En mode admin, `CompanyView.balance` est rempli pour toutes les entreprises ; `txs` (50 plus récentes) seulement pour l'entreprise « ciblée » du joueur admin (map serveur transitoire admin → companyId, vidée à `CLOSE`).

- [ ] **Step 1: Serveur** : `VIEW_TX` mémorise la cible de l'admin (`p.hasPermissions(2)` obligatoire) et renvoie l'état ; `view(...)` ajoute solde et historique selon la cible ; `bankAccess` reste `false` en admin (aucune action bancaire).
- [ ] **Step 2: Écran admin** : sur la fiche d'une entreprise, bouton **Transactions** (envoie `VIEW_TX`) qui affiche solde et liste paginée (mêmes colonnes que la tablette, lecture seule, réutiliser le rendu de lignes de l'onglet TRANSACTIONS).
- [ ] **Step 3: Compiler** : `.\gradlew.bat build`. Attendu : `BUILD SUCCESSFUL`.
- [ ] **Step 4: Commit** ; message `feat(entreprises): solde et transactions dans /entrepriseadmin`.

---

### Task 8: Entreprises : logique des factures

**Files:**
- Create: `InvoiceService.java`
- Modify: `data/EntrepriseData.java` (record `Invoice`, liste, NBT), `config/EntrepriseConfig.java` (4 champs), `EntrepriseService.java` (tick de relance, connexion), `EntrepriseWipe.java`

**Interfaces:**
- Consumes : `CompanyAccounts.canBank`, `BankService.transfer`/`hasAccount`, `PlayerWipeEvent`, `EntrepriseConfig`.
- Produces :
  - `EntrepriseData.Invoice` : `int id`, `int companyId`, `UUID issuer`, `String issuerName`, `UUID payer`, `String payerName`, `long cents`, `String description`, `long created`, `Status status` (`AWAITING_SIGNATURE, PAID, FAILED, REFUSED, CANCELED, EXPIRED`), `long lastAttempt` ; méthodes d'`EntrepriseData` : `Invoice newInvoice(...)`, `Invoice invoice(int id)`, `List<Invoice> invoicesOf(int companyId)`, `List<Invoice> invoicesOfPayer(UUID)` ; les factures closes sont limitées à 100 par entreprise (les plus anciennes disparaissent) ; NBT `invoices`.
  - `InvoiceService` (toutes `static`) :
    - `String create(ServerPlayer issuer, Company c, UUID payer, long cents, String description)` : erreur ou `null` ; prévient le client par `InvoiceService.notifier` (voir plus bas).
    - `String sign(ServerPlayer payer, int invoiceId)`, `String refuse(ServerPlayer payer, int invoiceId)`, `String cancel(ServerPlayer actor, int invoiceId)` (émetteur gérant ou OP).
    - `void tick(MinecraftServer s)` : expire les `AWAITING_SIGNATURE` plus vieilles que `facture_signature_secondes`, relance les `FAILED` toutes les `facture_relance_minutes`, annule les `FAILED` plus vieilles que `facture_expiration_jours` (0 = jamais).
    - `void onLogin(ServerPlayer p)` : tente les factures `FAILED` du joueur ; `void onWipe(UUID payer)` ; `void onCompanyClosed(int companyId)`.
  - Config : `facture_distance_blocs`, `facture_signature_secondes`, `facture_relance_minutes`, `facture_expiration_jours` (valeurs par défaut des Global Constraints).

Ordre de réalisation : cette tâche ne dépend pas du paquet client ; la création d'une facture ouvre l'écran du client par l'appel `InvoiceNotifier.open(ServerPlayer payer, Invoice inv, String companyName)`, une interface fonctionnelle statique `InvoiceService.notifier` (par défaut un message système « Facture reçue de X : Y € ») que la tâche 9 remplace par l'ouverture de l'écran.

- [ ] **Step 1: Modèle et NBT** : `Invoice`, liste, sérialisation et chargement dans `EntrepriseData` ; config.
- [ ] **Step 2: `create`** : refuse si `!canBank`, entreprise non ACTIVE, montant hors `(0, 1_000_000_000]`, description vide ou nettoyée (`clean`) trop longue (64 max), client = émetteur, client hors ligne, autre dimension ou plus loin que `facture_distance_blocs`, client déjà à 3 factures `AWAITING_SIGNATURE`.
- [ ] **Step 3: `sign`** : refuse si la facture n'existe pas, si `payer` n'est pas le client de la facture (id deviné), si le statut n'est pas `AWAITING_SIGNATURE` (donc un double clic ne paie jamais deux fois). Puis `transfer(s, payer, company.accountId, cents, BankTx.INCOME, "Facture : " + description, nom RP du client)`. `OK` : `PAID` et messages ; `INSUFFICIENT_FUNDS` ou `NO_ACCOUNT` du client : `FAILED`, `lastAttempt = now`, messages au client et à l'émetteur ; autre échec : message d'erreur, la facture reste `AWAITING_SIGNATURE`.
- [ ] **Step 4: `refuse`, `cancel`** : `REFUSED` (client, depuis `AWAITING_SIGNATURE`) avec message à l'émetteur ; `cancel` (gérant de l'entreprise émettrice ou OP) depuis `AWAITING_SIGNATURE` ou `FAILED`, `CANCELED`.
- [ ] **Step 5: `tick` et `onLogin`** : relance par `transfer` avec les mêmes garde-fous qu'à la signature (statut `FAILED` requis, montant entier seulement), succès → `PAID` et messages aux connectés ; un tick ne tente jamais une facture dont l'entreprise a été dissoute ou n'est plus ACTIVE. Brancher `tick` sur le tick serveur existant (toutes les 20 ticks, filtré par intervalle) et `onLogin` sur `PlayerLoggedInEvent`.
- [ ] **Step 6: Annulations** : appeler `onCompanyClosed` depuis `dissolve` et `REFUSE`, `onWipe` depuis `EntrepriseWipe` (annule les factures `AWAITING_SIGNATURE` et `FAILED` du joueur wipé, et celles des entreprises dissoutes).
- [ ] **Step 7: Compiler** : `.\gradlew.bat build`. Attendu : `BUILD SUCCESSFUL`.
- [ ] **Step 8: Commit** ; message `feat(entreprises): logique des factures (signature, prelevement differe, annulation)`.

---

### Task 9: Entreprises : écrans et réseau des factures

**Files:**
- Create: `client/InvoiceScreen.java`
- Modify: `network/ModNetwork.java` (paquets `InvoicePacket` serveur → client et `InvoiceActionPacket` client → serveur, codes d'action `CREATE_INVOICE = 23`, `CANCEL_INVOICE = 24`, `CompanyView` gagne `invoices` et `nearby`), `EntrepriseService.java` (`view`, `act`), `client/ClientNetworkHandler.java`, `client/EntrepriseScreen.java` (onglet FACTURES)

**Interfaces:**
- Consumes : `InvoiceService.create/cancel/sign/refuse`, `InvoiceService.notifier` (tâche 8).
- Produces :
  - `ModNetwork.InvoicePacket(int invoiceId, String company, String description, long cents, String issuerName)` : ouvre `InvoiceScreen` chez le client.
  - `ModNetwork.InvoiceActionPacket(int invoiceId, boolean sign)` : traité **sans** la garde `OPEN` (le client n'est pas au guichet) et uniquement pour le joueur dont l'UUID est le client de la facture (vérifié dans `InvoiceService.sign/refuse`).
  - `ModNetwork.InvoiceView(int id, long created, String payerName, String description, long cents, int status)` et `CompanyView.invoices` (50 plus récentes de l'entreprise, si `bankAccess`), `CompanyView.nearby` : `List<PlayerView(UUID id, String name)>` des joueurs à portée de la création (≤ `facture_distance_blocs`, même dimension, hors soi, nom RP), calculée seulement si `bankAccess`.
  - Actions : `CREATE_INVOICE` (`a` = UUID du client, `b` = montant texte euros, `c` = description) ; `CANCEL_INVOICE` (`n` = id de la facture).

- [ ] **Step 1: Paquets et vues** : encode et decode symétriques, enregistrement dans `ModNetwork.register` avec le protocole `"3"`.
- [ ] **Step 2: Serveur** : `view(...)` remplit `invoices` et `nearby` (droits `bankAccess`) ; `act` : `CREATE_INVOICE` et `CANCEL_INVOICE` (mode non admin, entreprise = `companyOf(p)` ; en mode admin `CANCEL_INVOICE` accepté avec `p.hasPermissions(2)`) ; `InvoiceService.notifier` remplacé pour envoyer `InvoicePacket`.
- [ ] **Step 3: `InvoiceScreen`** (client) : entreprise, description, montant (`MineNorthStyle.euros`), émetteur, boutons **Signer la facture** et **Refuser** qui envoient `InvoiceActionPacket` puis ferment l'écran ; se ferme aussi sans réponse si le serveur invalide la facture (statut vu à la prochaine action : la fermeture de l'écran sans clic ne fait rien, l'expiration du serveur s'applique).
- [ ] **Step 3b: Onglet FACTURES** dans `EntrepriseScreen` (visible si `bankAccess`) : bouton **Nouvelle facture** (liste des joueurs proches avec sélection, champ montant, champ description, bouton Créer), liste paginée des factures (date, client, description tronquée, montant, statut coloré, bouton **Annuler** sur `AWAITING_SIGNATURE` et `FAILED`). Cinq onglets : réduire les libellés ou passer sur deux rangées pour tenir dans 400 px.
- [ ] **Step 4: Compiler** : `.\gradlew.bat build`. Attendu : `BUILD SUCCESSFUL` (aucun accès à `net.minecraft.client.*` depuis les classes serveur).
- [ ] **Step 5: Commit** ; message `feat(entreprises): ecrans et reseau des factures`.

---

### Task 10: Documentation (README)

**Files:**
- Modify: `minenorth-entreprises/README.md`, `minenorth-api/README.md`

- [ ] **Step 1: `minenorth-entreprises/README.md`** : sections « Factures » (création, signature, échec et prélèvement différé, annulation, expiration, config), « Virement vers un joueur » (nom RP), « Transactions dans /entrepriseadmin », et les 4 nouveaux champs de config.
- [ ] **Step 2: `minenorth-api/README.md`** : ajouter `setAccountListed` à la section des comptes entreprise.
- [ ] **Step 3: Commit** dans chaque dépôt (chemins explicites) ; message `docs: factures, virements et ATM entreprise`.

---

### Task 11: Vérification en jeu (à faire par l'utilisateur)

**Files:** aucun.

- [ ] **Step 1: Construire API, EuroBank, entreprises** et copier les trois jars sur un serveur de test.
- [ ] **Step 2: Parcours :**
  - **A :** carte entreprise en main ouvre l'ATM : refus et message ; sans elle, ATM normal ; l'info-bulle mentionne « Achats uniquement ».
  - **B :** ATM → Virement : deux boutons ; la liste ne montre que les entreprises ACTIVE (pas celles en attente) ; virement de 10 € : le compte entreprise est crédité et la ligne `INCOME` apparaît dans la tablette ; solde insuffisant et montant `abc` refusés.
  - **E :** Virer à un joueur par nom RP (en ligne, puis hors ligne), nom inconnu, soi-même, motif ; la ligne apparaît dans l'historique avec le nom RP de l'acteur.
  - **D :** `/entrepriseadmin` : soldes dans la liste, bouton Transactions, lecture seule.
  - **C :** facture à un joueur proche : menu côté client, **Signer** avec fonds (paiement et ligne `INCOME`) ; **Signer** sans fonds (`FAILED`, message), puis crédit du compte client : prélèvement au cycle suivant ou à la connexion ; **Refuser** ; expiration sans réponse (300 s) ; annulation d'une facture en échec par le gérant et par un OP ; joueur trop loin ou hors ligne refusé ; 4e facture en attente refusée ; dissolution de l'entreprise avec une facture en échec ; wipe du client.
  - Mise en page : 5 onglets, listes et boutons dans la tablette et l'écran de facture.
- [ ] **Step 3: Noter les écarts**, les corriger dans la tâche concernée, relancer le parcours.
