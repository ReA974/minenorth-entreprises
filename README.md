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
- `facture_distance_blocs` (10, minimum 1) : distance maximale entre l'émetteur et le client à la création d'une facture.
- `facture_signature_secondes` (300, minimum 1) : délai laissé au client pour signer.
- `facture_relance_minutes` (10, minimum 1) : intervalle entre deux tentatives de prélèvement d'une facture signée mais impayée.
- `facture_expiration_jours` (0, minimum 0) : une facture signée mais impayée est annulée quand ce nombre de jours s'est écoulé depuis sa création (et non depuis l'échec) ; 0 = jamais.

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
  Maximum 10 M€ par opération. Voir aussi « Virement vers un joueur » et « Factures ».
- **Carte entreprise** : bouton de l'onglet TRANSACTIONS. À tenir **en main** pour payer, sinon la carte perso est utilisée.
  Une carte volée ou d'un titulaire qui n'est plus signataire est refusée.
- Historique : 200 transactions conservées par compte (paies, dépôts, virements, paiements, corrections admin).
- L'historique conservé reste de 200 transactions ; l'écran en affiche les 50 dernières.
- Dissolution (ou refus) : le solde va au patron, ou au Treasury s'il n'a pas de compte. Wipe du patron : solde au
  Treasury (source `entreprises:wipe`).
- Les frais de création restent payés par la carte perso.

## Tablette d'entreprise
Objet `minenorthentreprises:tablette_entreprise`, remis au PDG quand son entreprise devient active
(ou via le bouton « Obtenir la tablette » au guichet). Clic droit : gestion des employés, grades et salaires.
L'objet ne fonctionne que pour le PDG ; les co-gérants (grade avec droit de gestion) accèdent au même écran par le guichet
(`/entreprise <joueur>`). Le PDG ne peut pas changer le nom ni l'activité, et ne peut que **demander** la dissolution :
un OP l'accepte ou la refuse dans `/entrepriseadmin`.

Onglets : INFOS, EMPLOYÉS, GRADES (PDG), puis TRANSACTIONS et FACTURES (PDG et grades avec droit de gestion).

Onglet **TRANSACTIONS** : solde, boutons Déposer, Virer vers mon compte, Virer à un joueur et Obtenir ma carte
entreprise, puis historique paginé.

## Virement vers un joueur
Dans l'onglet TRANSACTIONS : montant, destinataire par son **nom RP** (« Prénom Nom », sans tenir compte de la casse,
jamais par pseudo) et motif facultatif (64 caractères au plus). Le destinataire doit avoir une carte d'identité et un
compte bancaire, et n'a pas besoin d'être connecté. Si plusieurs joueurs portent ce nom, le virement est refusé
(« Aucun joueur de ce nom ») : on ne devine pas. Pour se virer à soi-même, utiliser « Virer vers mon compte ».
Mêmes droits et même plafond (10 M€) que les autres opérations du compte.

## Factures
Un PDG ou un grade avec droit de gestion facture un client depuis l'onglet **FACTURES** (« Nouvelle facture ») :
- **Création** : le client est choisi parmi les joueurs à proximité (même dimension, à moins de `facture_distance_blocs`
  blocs, connecté). Description de 1 à 64 caractères, montant jusqu'à 10 M€. L'entreprise doit être active. On ne peut
  pas se facturer soi-même, et un client ne peut avoir que 3 factures en attente de signature.
- **Signature** : le client reçoit un écran « Facture » (entreprise, émetteur, description, montant) avec « Signer la
  facture » et « Refuser ». Il a `facture_signature_secondes` pour répondre ; fermer l'écran sans répondre ne fait
  rien, et la facture expire à la fin du délai ou à la déconnexion du client. L'émetteur est prévenu (payée, refusée,
  expirée).
- **Paiement** : à la signature, le montant entier est prélevé du compte du client vers le compte de l'entreprise.
  Si les fonds manquent (ou si le client n'a pas de compte), la facture passe en **échec** : elle reste due et est
  prélevée automatiquement toutes les `facture_relance_minutes`, et à chaque connexion du client. Le prélèvement
  fonctionne même si le client est hors ligne. Une facture payée est définitive.
- **Annulation** : bouton « Annuler » de l'onglet FACTURES pour une facture à signer ou en échec, par un gérant de
  l'entreprise ou un OP. Le client est prévenu. Avec `facture_expiration_jours` > 0, une facture en échec est annulée
  dès que ce nombre de jours s'est écoulé depuis sa création (pas depuis l'échec).
- **Fermeture** : la dissolution ou le refus de l'entreprise, ou le wipe du client, annule les factures ouvertes (à
  signer ou en échec). Une entreprise qui n'est plus active ne peut plus rien prélever.
- L'onglet liste les 50 dernières factures de l'entreprise, avec leur statut.

## Transactions dans /entrepriseadmin
Dans la fiche d'une entreprise, les onglets **TRANSACTIONS** et **FACTURES** sont disponibles en **lecture seule** pour
les OP : solde, 50 dernières opérations et liste des factures de l'entreprise ciblée. Aucune action bancaire
(dépôt, virement, carte, création de facture) n'est possible depuis ce mode ; seule l'annulation d'une facture ouverte
l'est. L'entreprise affichée est celle de l'onglet cliqué ; le solde de l'entreprise figure dans son onglet INFOS et dans son onglet TRANSACTIONS (pas dans les lignes de la liste).

## Distributeur (ATM)
- Une carte entreprise tenue en main est **refusée** par le distributeur (« Cette carte n'est pas reconnue par le
  distributeur. ») : elle ne sert qu'à payer.
- Page Virement : « Virement à un joueur » ou « Virement à une entreprise ». La liste (paginée) contient les
  entreprises **actives** (au plus 100 : `StatePacket.MAX_COMPANIES` côté banque) ; une entreprise en attente, refusée ou dissoute n'y figure pas. Le virement part du compte
  perso du joueur, avec sa carte.

## API
`EntrepriseApi.accountOf(server, companyId)` renvoie l'UUID du compte entreprise (ou `null`). Les autres mods s'en
servent avec `MineNorth.bank().payFromAccount(...)` pour payer depuis ce compte (versé au Treasury avec une source) et
`MineNorth.bank().transfer(...)` pour créditer ou débiter de compte à compte.

## Licence

**Tous droits réservés - MineNorthRP.** Réutilisation, copie, modification, décompilation / ingénierie
inverse (y compris par outils d'intelligence artificielle) et utilisation pour entraîner une IA sont
**interdites** sans autorisation écrite. Voir [LICENSE](LICENSE).

## Distributeur automatique
Bloc `minenorthentreprises:distributeur` (2 blocs de haut, onglet Outils du créatif, recette : fer, vitres, redstone, entonnoir).
Ouvre 24 h/24, même commerce fermé : les ventes sont créditées **directement sur le compte de l'entreprise** (catégorie
INCOME, « Distributeur : article »), à retrouver dans l'onglet TRANSACTIONS et à dépenser avec la carte entreprise.
- **Pose** : patron ou gérant (grade « manage ») d'une entreprise **active** uniquement ; le distributeur est rattaché à
  cette entreprise. Une entreprise dissoute ou en attente met ses distributeurs « hors service ».
- **Client** (clic droit) : choisit un article puis « Acheter » ; un article par achat, payé par **carte bancaire**
  (sa carte sur lui, solde suffisant), l'article va dans l'inventaire (sinon au sol). Le gérant voit la même vue.
- **Gérant** (**Maj + clic droit**, même avec des objets en main) : 18 emplacements (3 × 6) qui se remplissent comme un
  coffre (clic, Maj+clic). Cliquer sur le **prix** sous un emplacement, saisir le montant (jusqu'à 100 000 €, 0 = pas à
  vendre) puis « Valider ». Les articles s'affichent sur les étagères du bloc.
- **Casser** : gérants de l'entreprise et OP uniquement ; le stock tombe au sol.
- Les prix et le stock sont enregistrés dans le bloc ; le serveur revérifie tout (droits, prix, solde, entreprise active).
Le protocole réseau passe à la version 5 : mettre à jour client et serveur ensemble.

## Cabine de peinture (entreprise de modification automobile)
Bloc `cabine_peinture` (onglet créatif MineNorth, recette fer / dye bleu / diamant / redstone). Le service est rendu par le **personnel** de l'entreprise
(patron, gérants **et employés**) sur le véhicule d'un client, qui est ensuite **facturé** avec le système de factures (onglet FACTURES) : la cabine ne prend
aucun paiement.
- **Pose et casse** : patron / gérants d'une entreprise **active** (et OP) ; la cabine est rattachée à l'entreprise du poseur. Les autres joueurs voient « Réservé au personnel ».
- **Utilisation** : garer le véhicule (Immersive Vehicles) à **moins de 6 blocs** de la cabine, descendre du véhicule, clic droit sur la cabine, choisir une
  peinture (les sous-définitions du modèle MTS), « Appliquer ».
- Le véhicule est recréé à l'identique (pièces, carburant, dégâts, propriétaire, assurance) avec la nouvelle peinture ; si cela échoue, rien n'est modifié.
- Réseau : protocole 7, serveur et clients ensemble. L'accès à MTS se fait par réflexion (Immersive Vehicles 24.0.0).
- Le **banc de puissance** (diagnostic moteur et reprogrammation) est dans un mod séparé : `minenorth-banc`.
