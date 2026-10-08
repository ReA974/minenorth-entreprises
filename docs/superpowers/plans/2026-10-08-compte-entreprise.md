# Compte entreprise EuroBank : plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Chaque entreprise possède un vrai compte EuroBank (ouvert dès la création) qui finance les paies, reçoit dépôts et recettes, paie par une carte entreprise, et dont l'historique s'affiche dans un onglet TRANSACTIONS de la tablette.

**Architecture:** `minenorth-api` expose de nouvelles méthodes `default` sur `BankService` ; `minenorth-eurobank` les implémente (comptes marqués « entreprise », signataires, historique borné, carte entreprise) ; `minenorth-entreprises` pilote le cycle de vie du compte via une classe `CompanyAccounts` et l'affiche dans la tablette.

**Tech Stack:** Java 17, Forge 1.20.1 (47.4.10), ForgeGradle, JUnit 5 (ajouté à `minenorth-eurobank` seulement).

**Spec:** `docs/superpowers/specs/2026-10-08-compte-entreprise-design.md` (dans `minenorth-entreprises`).

**Écarts avec la spec (à reporter dans la spec à la fin de la tâche 1) :**
- `BankTx` porte un `actor` (nom RP, `String`) et un `label` (la contrepartie y est incluse) ; pas de champ `counterparty`.
- Le montant de dépôt et de virement passe en texte euros dans `ActionPacket.a`, lu par `EntrepriseService.euros(String)` existant ; pas dans `n`.

## Global Constraints

- Dépôts : `minenorth-api` et `minenorth-eurobank` sur la branche `feat/entreprise` ; `minenorth-entreprises` sur `feat/compte-entreprise-bank`. Les trois dépôts ont des fichiers `.gradle/` et `.idea/` suivis par git et modifiés : ne **jamais** utiliser `git add -A` ni `git commit -a`, toujours des chemins explicites.
- Montants en centimes (`long`). Opération max par dépôt, virement ou paiement : `1_000_000_000L` (10 M€) ; au-delà, refus.
- Nouvelles méthodes de `BankService` : `default`, jamais abstraites. `BankService.NONE` reste cohérent (refus ou `false`).
- Historique : 200 transactions par compte entreprise (constante `TxLog.MAX = 200`) ; 50 envoyées à la tablette (`TX_SENT = 50`).
- Accès tablette, dépôt, virement, carte, paiement : patron ou grade `manage == true`. Vérifié côté serveur.
- Protocole réseau du mod entreprises : `"1"` passe à `"2"`.
- Règles de nommage RP : afficher `MineNorth.displayName(...)`, jamais le pseudo.
- Pas de sanction automatique en cas de compte à 0 : message uniquement.
- Les jars se consomment ainsi : `minenorth-api` d'abord (`../minenorth-api/build/libs/minenorth_api-1.0.0.jar`), puis eurobank et entreprises. Après chaque changement d'API, reconstruire l'API avant les autres.
- Commits : seulement après accord de l'utilisateur au lancement de l'exécution.

## Review Focus

1. **Montant invalide** (0, négatif, texte non numérique, > 10 M€) sur dépôt, virement, paiement : refus, aucun mouvement. Test : tâche 3 (JUnit sur `TxLog`/validation) et tâche 7 (vérification manuelle).
2. **Dissolution ou wipe avec solde et patron sans compte EuroBank ou hors ligne** : le solde ne doit jamais disparaître ni rester sur un compte fermé. Test : tâche 5, étape de vérification.
3. **Banque indisponible (`BankService.NONE`) à la création** : refuser avant de prélever les frais. Test : tâche 5.
4. **Gérant rétrogradé, licencié ou patron changé tenant encore sa carte** : refus immédiat, y compris après redémarrage (signataires persistés et resynchronisés). Test : tâches 2, 4 et 5.
5. **Monde existant avec des entreprises sans `accountId`** : compte ouvert à 0 au chargement, sans plantage. Test : tâche 5.

---

## File Structure

| Fichier | Rôle |
|---|---|
| `minenorth-api/.../fr/minenorth/api/BankTx.java` (créer) | Record d'une transaction, partagé API, banque, tablette |
| `minenorth-api/.../BankService.java` (modifier) | Nouvelles méthodes `default` |
| `minenorth-eurobank/.../TxLog.java` (créer) | Historique borné, pur Java, testé |
| `minenorth-eurobank/.../BankData.java` (modifier) | Comptes entreprise, signataires, historique, NBT |
| `minenorth-eurobank/.../api/BankProvider.java` (modifier) | Implémente les nouvelles méthodes, enregistre les transactions |
| `minenorth-eurobank/.../api/BankApi.java` (modifier) | `charge` et `check` : règle de la carte entreprise en main |
| `minenorth-eurobank/.../items/BusinessCardItem.java` (créer), `ModItems.java` (modifier) | Item carte entreprise |
| `minenorth-entreprises/.../CompanyAccounts.java` (créer) | Cycle de vie du compte, dépôt, virement, signataires, alerte |
| `minenorth-entreprises/.../EntrepriseService.java`, `data/EntrepriseData.java`, `EntrepriseWipe.java`, `api/EntrepriseApi.java`, `network/ModNetwork.java`, `client/EntrepriseScreen.java` (modifier) | Intégration |

Chemins complets : `src/main/java/fr/minenorth/api/` (API), `src/main/java/com/minenorth_eurobank/` (banque), `src/main/java/fr/minenorth/entreprises/` (entreprises).

---

### Task 1: API : `BankTx` et méthodes du compte entreprise

**Files:**
- Create: `minenorth-api/src/main/java/fr/minenorth/api/BankTx.java`
- Modify: `minenorth-api/src/main/java/fr/minenorth/api/BankService.java`
- Modify: `minenorth-entreprises/docs/superpowers/specs/2026-10-08-compte-entreprise-design.md` (écarts listés en tête de ce plan)

**Interfaces:**
- Produces (utilisé par les tâches 2 à 8) :
  - `record BankTx(long time, String category, long cents, long balanceAfter, String label, String actor)` ; `cents` est signé du point de vue du compte ; constantes `String` : `BankTx.DEPOSIT`, `WITHDRAW`, `SALARY`, `PAYMENT`, `INCOME`, `ADMIN`.
  - Dans `BankService`, toutes `default` :
    - `boolean openBusinessAccount(MinecraftServer s, UUID accountId, String label)` : idempotent, vrai si le compte entreprise existe après l'appel.
    - `boolean renameAccount(MinecraftServer s, UUID accountId, String label)`
    - `boolean closeAccount(MinecraftServer s, UUID accountId)` : faux si pas un compte entreprise ou solde différent de 0.
    - `void setSigners(MinecraftServer s, UUID accountId, Set<UUID> signers)`
    - `PayResult transfer(MinecraftServer s, UUID from, UUID to, long cents, String category, String label, String actor)` : par défaut appelle `transfer(s, from, to, cents)` ; la version de la banque enregistre l'historique.
    - `PayResult payFromAccount(MinecraftServer s, UUID accountId, long cents, String source, UUID actor)` : `actor == null` signifie appel système (pas de contrôle de signataire).
    - `List<BankTx> history(MinecraftServer s, UUID accountId, int limit)`
    - `boolean giveBusinessCard(ServerPlayer p, UUID accountId, String companyName)`

- [ ] **Step 1: Créer `BankTx`** (record + six constantes) dans le package `fr.minenorth.api`.
- [ ] **Step 2: Ajouter les huit méthodes `default` à `BankService`.** Valeurs par défaut : `false`, rien, `UNAVAILABLE`, `List.of()`. La surcharge `transfer` à 7 arguments délègue à `transfer(s, from, to, cents)`.
- [ ] **Step 3: Corriger les deux lignes d'écart dans la spec** (section 4 : `BankTx` sans `counterparty`, avec `actor`; section 7 : montant dans `a`).
- [ ] **Step 4: Compiler.** Dans `minenorth-api` : `.\gradlew.bat build`. Attendu : `BUILD SUCCESSFUL` et `build/libs/minenorth_api-1.0.0.jar` mis à jour.
- [ ] **Step 5: Commit** (dans `minenorth-api`, branche `feat/entreprise`) :

```bash
git add src/main/java/fr/minenorth/api/BankTx.java src/main/java/fr/minenorth/api/BankService.java
git commit -m "feat(api): comptes entreprise, historique et carte entreprise dans BankService"
```

---

### Task 2: Banque : stockage (comptes entreprise, signataires, historique)

**Files:**
- Create: `minenorth-eurobank/src/main/java/com/minenorth_eurobank/TxLog.java`
- Modify: `minenorth-eurobank/src/main/java/com/minenorth_eurobank/BankData.java`
- Modify: `minenorth-eurobank/build.gradle` (ajouter `testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'` et `test { useJUnitPlatform() }`)
- Test: `minenorth-eurobank/src/test/java/com/minenorth_eurobank/TxLogTest.java`

**Interfaces:**
- Consumes: `BankTx` (tâche 1).
- Produces :
  - `TxLog` : `static final int MAX = 200`, `void add(BankTx tx)` (supprime les plus anciennes au-delà de `MAX`), `List<BankTx> latest(int limit)` (du plus récent au plus ancien).
  - Dans `BankData` : `boolean isBusiness(UUID id)`, `void openBusiness(UUID id, String label)`, `void closeBusiness(UUID id)` (retire solde, nom, signataires, historique), `Set<UUID> signers(UUID account)`, `void setSigners(UUID account, Set<UUID> s)`, `void record(UUID account, BankTx tx)`, `List<BankTx> history(UUID account, int limit)`. `findByName` et `all()` ignorent les comptes entreprise ; `total()` les inclut. Tout est sauvegardé en NBT (listes `business`, `signers`, `history`).

- [ ] **Step 1: Ajouter JUnit à `build.gradle`** comme indiqué ci-dessus.
- [ ] **Step 2: Écrire les tests** dans `TxLogTest` : `keepsOnlyLast200` (ajouter 250 transactions, `latest(500).size() == 200` et la première renvoyée est la 250e), `latestIsNewestFirst` (ajouter 3, `latest(2)` renvoie la 3e puis la 2e), `latestHonoursLimit`.
- [ ] **Step 3: Lancer, constater l'échec.** `.\gradlew.bat test --tests "*TxLogTest*"`. Attendu : échec de compilation (`TxLog` absent).
- [ ] **Step 4: Implémenter `TxLog`** (liste en mémoire, pure Java, sans type Minecraft).
- [ ] **Step 5: Lancer, constater le succès.** Même commande, attendu : 3 tests PASS.
- [ ] **Step 6: Étendre `BankData`** avec les méthodes ci-dessus. Les transactions s'enregistrent en `CompoundTag` (`time`, `cat`, `cents`, `after`, `label`, `actor`), un `ListTag` par compte entreprise. Même style que `balances`.
- [ ] **Step 7: Vérifier `findByName` et `all()`** : ouvrir un compte entreprise de test dans une méthode de test `BankDataBusinessTest` si le classpath de test charge `CompoundTag` ; sinon le vérifier en jeu (tâche 9). Contrôle minimal : le projet compile.
- [ ] **Step 8: Compiler.** `.\gradlew.bat build`. Attendu : `BUILD SUCCESSFUL`.
- [ ] **Step 9: Commit** (dans `minenorth-eurobank`, `feat/entreprise`) :

```bash
git add build.gradle src/main/java/com/minenorth_eurobank/TxLog.java src/main/java/com/minenorth_eurobank/BankData.java src/test
git commit -m "feat(bank): comptes entreprise, signataires et historique borne"
```

---

### Task 3: Banque : `BankProvider` implémente l'API entreprise

**Files:**
- Modify: `minenorth-eurobank/src/main/java/com/minenorth_eurobank/api/BankProvider.java`

**Interfaces:**
- Consumes: API de la tâche 1 ; `BankData` de la tâche 2.
- Produces : toutes les méthodes de la tâche 1 implémentées. Règles :
  - `openBusinessAccount` : `BankData.open` puis `openBusiness`.
  - `closeAccount` : refuse (`false`) si pas entreprise ou solde différent de 0.
  - `payFromAccount` : refuse `INVALID_AMOUNT` si `cents <= 0` ou `> 1_000_000_000L` ; `NO_ACCOUNT` si pas entreprise ; `FOREIGN_CARD` si `actor != null` et absent des signataires ; `INSUFFICIENT_FUNDS` sinon ; débite, `treasury().collect(s, cents, source)`, enregistre `PAYMENT` (acteur = nom RP via `BankData.name`).
  - `transfer` 7 arguments : exécute le virement existant ; si `from` ou `to` est un compte entreprise, enregistre une transaction de chaque côté entreprise (signe négatif côté `from`, positif côté `to`). Montant > 10 M€ : `INVALID_AMOUNT`.
  - `debit` et `refund` : enregistrent aussi quand le compte concerné est entreprise.
  - `history` : `BankData.history`.
  - `giveBusinessCard` : dépend de la tâche 4 ; en attendant, laisser le comportement par défaut et brancher à l'étape 4.2.

- [ ] **Step 1: Implémenter les méthodes ci-dessus** dans `BankProvider`, avec une aide privée `record(MinecraftServer, UUID, long signedCents, String cat, String label, String actor)` qui lit le solde après opération.
- [ ] **Step 2: Compiler.** `.\gradlew.bat build`. Attendu : `BUILD SUCCESSFUL`.
- [ ] **Step 3: Commit** :

```bash
git add src/main/java/com/minenorth_eurobank/api/BankProvider.java
git commit -m "feat(bank): BankProvider implemente les comptes entreprise"
```

---

### Task 4: Banque : carte entreprise et règle de paiement

**Files:**
- Create: `minenorth-eurobank/src/main/java/com/minenorth_eurobank/items/BusinessCardItem.java`
- Modify: `.../items/ModItems.java`, `.../api/BankApi.java`, `.../api/BankProvider.java`
- Modify (ressources) : `src/main/resources/assets/minenorth_eurobank/lang/*.json` et un modèle d'item, sur le modèle de la carte perso.

**Interfaces:**
- Consumes: `BankData.isBusiness`, `signers`, `record` (tâche 2).
- Produces :
  - `BusinessCardItem.create(ServerPlayer p, UUID account, String companyName) -> ItemStack` : NBT `Account`, `Holder` (UUID du joueur), `CompanyName`.
  - `BusinessCardItem.activeAccount(ServerPlayer p) -> UUID` : l'`Account` de la carte entreprise tenue en **main principale**, ou `null` s'il n'y en a pas.
  - `BusinessCardItem.validFor(ServerPlayer p, ItemStack s, BankData d) -> boolean` : `Holder` égal au joueur, compte entreprise, joueur dans les signataires.

- [ ] **Step 1: Créer l'item** et l'enregistrer dans `ModItems` (même registre que `CARD`), avec texte de survol « Entreprise : <nom> / Titulaire : <nom RP> ».
- [ ] **Step 2: Brancher `giveBusinessCard`** dans `BankProvider` : `p.getInventory().add(stack)` sinon `p.drop(stack, false)`, retourne `true`.
- [ ] **Step 3: Modifier `BankApi.check` et `BankApi.charge`.** Si le joueur tient une `BusinessCardItem` en main principale : refus `FOREIGN_CARD` si `validFor` est faux ; refus `INVALID_AMOUNT` si montant `<= 0` ou `> 1_000_000_000L` ; `INSUFFICIENT_FUNDS` si le solde du compte est insuffisant ; en cas de succès de `charge`, débiter le compte entreprise, `treasury().collect(...)`, enregistrer `PAYMENT` avec la source comme libellé et le nom RP du joueur comme acteur. Sans carte entreprise en main, comportement actuel inchangé.
- [ ] **Step 4: Compiler.** `.\gradlew.bat build`. Attendu : `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit** :

```bash
git add src/main/java/com/minenorth_eurobank src/main/resources
git commit -m "feat(bank): carte entreprise et paiement sur le compte entreprise"
```

---

### Task 5: Entreprises : `accountId`, cycle de vie du compte, signataires

**Files:**
- Create: `minenorth-entreprises/src/main/java/fr/minenorth/entreprises/CompanyAccounts.java`
- Modify: `data/EntrepriseData.java`, `EntrepriseService.java`, `EntrepriseWipe.java`, `build.gradle` si besoin de recompiler contre l'API reconstruite (tâche 1)

**Interfaces:**
- Consumes: `BankService` (tâche 1) via `MineNorth.bank()`.
- Produces (`CompanyAccounts`, toutes `static`) :
  - `boolean canBank(Company c, UUID id)` : patron ou grade avec `manage == true`.
  - `boolean open(MinecraftServer s, Company c)` : `openBusinessAccount(s, c.accountId, c.name)`.
  - `void rename(MinecraftServer s, Company c)`.
  - `void syncSigners(MinecraftServer s, Company c)` : patron plus tous les membres avec `manage`.
  - `void close(MinecraftServer s, Company c, boolean toTreasury)` : solde transféré au patron (`transfer`, catégorie `WITHDRAW`, libellé « Dissolution ») ; si impossible (patron sans compte) ou `toTreasury == true`, `payFromAccount(s, c.accountId, solde, "entreprises:dissolution" ou "entreprises:wipe", null)` ; puis `closeAccount`.
  - Dans `Company` : champ `UUID accountId`, sérialisé en NBT ; généré si absent au chargement.

- [ ] **Step 1: Ajouter `accountId` à `Company`** (`EntrepriseData`), généré dans `create(...)` et au `load` si absent.
- [ ] **Step 2: Créer `CompanyAccounts`** avec les méthodes ci-dessus.
- [ ] **Step 3: Brancher le cycle de vie dans `EntrepriseService` :**
  - `create(...)` : vérifier `MineNorth.bank() != BankService.NONE` **avant** de prélever les frais ; ouvrir le compte et synchroniser les signataires juste après `d.create(...)`. `adminCreate` fait de même.
  - `dissolve(...)` et refus (`REFUSE`) : `CompanyAccounts.close(..., false)` avant `d.remove`.
  - `EDIT` (renommage) : `CompanyAccounts.rename`.
  - À la fin de `handle(...)` pour toute action autre que `CLOSE`, resynchroniser les signataires de la société concernée (embauche, licenciement, changement de grade, de droit `manage`, de patron).
  - Au démarrage du serveur (`ServerStartedEvent`) : pour chaque société, ouvrir le compte s'il manque (migration) et resynchroniser les signataires.
- [ ] **Step 4: `EntrepriseWipe`** : appeler `CompanyAccounts.close(..., true)` pour chaque société dissoute à cause du wipe du patron.
- [ ] **Step 5: Compiler.** Reconstruire d'abord l'API (`.\gradlew.bat build` dans `minenorth-api`), puis `.\gradlew.bat build` dans `minenorth-entreprises`. Attendu : `BUILD SUCCESSFUL`.
- [ ] **Step 6: Vérification manuelle (voir tâche 9)** : création, refus, dissolution avec solde, wipe, migration d'un monde sans `accountId`.
- [ ] **Step 7: Commit** (dans `minenorth-entreprises`, `feat/compte-entreprise-bank`) :

```bash
git add src/main/java docs
git commit -m "feat(entreprises): compte EuroBank ouvert a la creation, signataires synchronises"
```

---

### Task 6: Entreprises : paies sur le compte entreprise et alerte

**Files:**
- Modify: `EntrepriseService.java` (méthode `payroll`, lignes ~411-437)

**Interfaces:**
- Consumes: `CompanyAccounts.canBank`, `BankService.transfer(..., category, label, actor)` (tâche 1).
- Produces: `payroll(MinecraftServer)` qui prélève sur `c.accountId`.

- [ ] **Step 1: Remplacer le prélèvement** : pour chaque employé connecté, avec compte et salaire `> 0` : `bank.transfer(s, c.accountId, m.id, g.salary, BankTx.SALARY, "Salaire " + g.name, "Entreprise")`. Si `INSUFFICIENT_FUNDS` : `unpaid++`, message à l'employé « compte de l'entreprise insuffisant ». Autre échec : message générique, pas d'alerte de redressement.
- [ ] **Step 2: Alerte unique par cycle.** Après la boucle d'une société, si `unpaid > 0` pour solde insuffisant : message « Les comptes de « X » sont à 0 ou insuffisants : l'entreprise risque le redressement judiciaire. » à chaque membre connecté pour qui `canBank` est vrai. Un seul envoi par société et par cycle.
- [ ] **Step 3: Message de succès au patron** : « « X » : <montant> de salaires prélevés sur le compte entreprise. » (remplace l'ancien message sur le compte perso).
- [ ] **Step 4: Compiler.** `.\gradlew.bat build`. Attendu : `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit** :

```bash
git add src/main/java/fr/minenorth/entreprises/EntrepriseService.java
git commit -m "feat(entreprises): paies prelevees sur le compte entreprise, alerte compte vide"
```

---

### Task 7: Entreprises : dépôt, virement, carte entreprise, API

**Files:**
- Modify: `CompanyAccounts.java`, `EntrepriseService.java` (méthode `act`, switch des actions), `network/ModNetwork.java` (constantes), `api/EntrepriseApi.java`

**Interfaces:**
- Consumes: `euros(String) -> long` (existant, `-1` si invalide), `canBank`.
- Produces :
  - Constantes `ModNetwork.DEPOSIT = 18`, `WITHDRAW = 19`, `GET_BUSINESS_CARD = 20`.
  - `CompanyAccounts.deposit(ServerPlayer p, Company c, long cents) -> String` (message d'erreur ou `null`) ; `withdraw` de même ; `giveCard(ServerPlayer p, Company c) -> boolean`.
  - `EntrepriseApi.accountOf(MinecraftServer s, int companyId) -> UUID` (`null` si absent).

- [ ] **Step 1: `deposit`** : refuse si `!canBank`, entreprise non ACTIVE, `cents <= 0 || cents > 1_000_000_000L`, ou résultat de `transfer(s, p.getUUID(), c.accountId, cents, BankTx.DEPOSIT, "Dépôt", nom RP)` différent de `OK` (le message vient de `PayResult.message()`).
- [ ] **Step 2: `withdraw`** : mêmes contrôles, `transfer(s, c.accountId, p.getUUID(), cents, BankTx.WITHDRAW, "Virement vers " + nom RP, nom RP)`.
- [ ] **Step 3: `giveCard`** : vérifie `canBank` et ACTIVE, appelle `giveBusinessCard(p, c.accountId, c.name)`.
- [ ] **Step 4: Ajouter les trois cas dans `act`**, avec le montant lu par `euros(k.a())`. Texte non numérique : « Montant invalide. ».
- [ ] **Step 5: `EntrepriseApi.accountOf`.**
- [ ] **Step 6: Compiler.** `.\gradlew.bat build`. Attendu : `BUILD SUCCESSFUL`.
- [ ] **Step 7: Commit** :

```bash
git add src/main/java
git commit -m "feat(entreprises): depot, virement, carte entreprise et accountOf"
```

---

### Task 8: Entreprises : réseau et onglet TRANSACTIONS

**Files:**
- Modify: `network/ModNetwork.java` (`CompanyView`, `StatePacket`, protocole `"1"` vers `"2"`), `EntrepriseService.java` (`view`, `send`), `client/EntrepriseScreen.java` (`buildCompany`, onglets)

**Interfaces:**
- Consumes: `BankService.balance`, `BankService.history`, `CompanyAccounts.canBank`, `BankTx` (tâche 1).
- Produces :
  - `record TxView(long time, String category, long cents, long balanceAfter, String label, String actor)` dans `ModNetwork`.
  - `CompanyView` reçoit `long balance`, `boolean bankAccess`, `List<TxView> txs` (rempli seulement hors mode admin et si `bankAccess`, 50 max).

- [ ] **Step 1: Étendre `CompanyView` et son codec** (écriture et lecture symétriques), passer la version du canal à `"2"`.
- [ ] **Step 2: Remplir ces champs dans `view(...)`** : `bankAccess = canBank(c, viewer)`, solde et historique via `MineNorth.bank()`. `view` reçoit donc le joueur concerné en paramètre supplémentaire ; adapter les appels dans `send`.
- [ ] **Step 3: Dans `EntrepriseScreen.buildCompany`**, ajouter un onglet **TRANSACTIONS** visible si `bankAccess` : solde en haut, champ montant, boutons **Déposer**, **Virer vers mon compte**, **Obtenir ma carte entreprise** (envoient `DEPOSIT`, `WITHDRAW`, `GET_BUSINESS_CARD`), liste paginée des transactions (date, libellé, montant vert ou rouge, solde après, acteur). Réutiliser `MineNorthStyle`, `MineNorthButton` et `MineNorthStyle.euros(...)`.
- [ ] **Step 4: Afficher le solde aussi dans l'onglet INFOS** pour les utilisateurs avec `bankAccess`.
- [ ] **Step 5: Compiler.** `.\gradlew.bat build`. Attendu : `BUILD SUCCESSFUL`.
- [ ] **Step 6: Commit** :

```bash
git add src/main/java
git commit -m "feat(entreprises): onglet TRANSACTIONS et paquets reseau v2"
```

---

### Task 10: Documentation (README)

**Files:**
- Modify: `minenorth-entreprises/README.md`
- Modify: `minenorth-api/README.md` (tableau des règles de cohérence, ligne « Virement » et fournisseurs)

- [ ] **Step 1: `minenorth-entreprises/README.md`** : remplacer la section « Salaires » (« prélevés sur le compte bancaire du patron ») par : salaires prélevés sur le **compte entreprise** ; compte vide : paie non versée et alerte « redressement judiciaire » au patron et aux gérants connectés (une fois par cycle), sans sanction automatique.
- [ ] **Step 2: Ajouter une section « Compte entreprise »** : compte EuroBank ouvert dès la création (fermé à un refus ou une dissolution) ; accès réservé au PDG et aux grades avec droit de gestion ; dépôt depuis le compte perso, virement vers le compte perso ; carte entreprise (bouton de l'onglet) à tenir **en main** pour payer, sinon la carte perso est utilisée ; dissolution : solde au patron (au Treasury s'il n'a pas de compte) ; wipe du patron : solde au Treasury (`entreprises:wipe`) ; limite par opération 10 M€ ; 200 transactions conservées par compte.
- [ ] **Step 3: Compléter la section « Tablette d'entreprise »** : nouvel onglet TRANSACTIONS (solde, dépôt, virement, carte, historique) ; l'onglet apparaît pour le PDG (tablette) et les co-gérants (guichet). Corriger la phrase « Seul le PDG peut l'utiliser » pour qu'elle reste exacte.
- [ ] **Step 4: Mentionner l'API** : `EntrepriseApi.accountOf(server, companyId)` et son usage avec `payFromAccount` et `transfer`.
- [ ] **Step 5: `minenorth-api/README.md`** : ajouter au tableau des règles une ligne « Compte entreprise / carte entreprise » (`openBusinessAccount`, `payFromAccount`, `history`, `giveBusinessCard`) et noter que les nouvelles méthodes de `BankService` sont `default`.
- [ ] **Step 6: Commit** dans chaque dépôt concerné (chemins explicites) :

```bash
git add README.md
git commit -m "docs: compte entreprise, carte entreprise et API"
```

---

### Task 9: Vérification en jeu

**Files:** aucun.

- [ ] **Step 1: Construire les trois jars** (`minenorth-api`, puis `minenorth-eurobank`, puis `minenorth-entreprises`) et les placer dans le dossier `mods` d'un serveur de test, avec `minenorth_eurobank` à jour (le fichier `libs/minenorth_eurobank-1.0.0.jar` de `minenorth-entreprises` n'est pas utilisé par le build).
- [ ] **Step 2: Parcours à exécuter et cocher :**
  - Créer une entreprise (avec validation OP) : le compte existe (`/bank` ou tablette), solde 0, absent de l'admin des comptes joueurs.
  - Refuser une entreprise : frais remboursés, compte fermé.
  - Déposer 100 € puis virer 40 € : soldes et lignes d'historique corrects ; texte « abc », `0`, `-5`, `20000000` refusés sans mouvement.
  - Un employé simple n'a pas l'onglet ; un Co-gérant l'a.
  - Salaire avec solde suffisant ; puis solde à 0 : paie non versée, une seule alerte « redressement judiciaire » par cycle pour le patron et le gérant.
  - Obtenir la carte entreprise : paiement boutique avec la carte en main débite l'entreprise (ligne `PAYMENT` visible) ; sans la carte en main, c'est le compte perso.
  - Carte d'un autre joueur et carte d'un gérant rétrogradé ou licencié : paiement refusé, aussi après redémarrage du serveur.
  - Dissolution avec solde : solde reçu par le patron ; patron sans compte : solde au Treasury (ligne `entreprises:dissolution`).
  - Wipe du patron avec solde : `entreprises:wipe` au Treasury.
  - Charger un monde où des entreprises n'ont pas d'`accountId` : compte ouvert à 0 au démarrage.
  - Banque absente : création refusée sans prélever les frais.
- [ ] **Step 3: Noter les écarts**, les corriger dans la tâche concernée, puis relancer le parcours.
