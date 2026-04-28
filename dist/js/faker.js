// Österreich-realistische Faker für Patientenstammdaten.
// Keine externe Library — kleine kuratierte Listen reichen für Übungszwecke.

const VORNAMEN_M = [
    'Maximilian',
    'Lukas',
    'Tobias',
    'Florian',
    'Stefan',
    'Andreas',
    'Markus',
    'Christoph',
    'Bernhard',
    'Gerhard',
    'Hans',
    'Josef',
    'Franz',
    'Karl',
    'Walter',
    'Friedrich',
    'Manfred',
    'Peter',
    'Wolfgang',
    'Helmut',
    'Alexander',
    'Daniel',
    'Michael',
    'Thomas',
    'Martin',
];

const VORNAMEN_F = [
    'Anna',
    'Sophie',
    'Lena',
    'Marie',
    'Sarah',
    'Lisa',
    'Julia',
    'Katharina',
    'Magdalena',
    'Theresa',
    'Maria',
    'Elisabeth',
    'Christine',
    'Brigitte',
    'Hannelore',
    'Gertrude',
    'Edith',
    'Hildegard',
    'Renate',
    'Ingrid',
    'Eva',
    'Barbara',
    'Andrea',
    'Petra',
    'Christa',
];

const NACHNAMEN = [
    'Gruber',
    'Huber',
    'Bauer',
    'Wagner',
    'Müller',
    'Pichler',
    'Steiner',
    'Moser',
    'Mayer',
    'Hofer',
    'Leitner',
    'Berger',
    'Fuchs',
    'Eder',
    'Fischer',
    'Schmid',
    'Winkler',
    'Weber',
    'Schneider',
    'Reiter',
    'Brunner',
    'Lang',
    'Baumgartner',
    'Auer',
    'Wallner',
    'Egger',
    'Wimmer',
    'Aigner',
    'Köck',
    'Lechner',
    'Stocker',
    'Holzer',
    'Maier',
    'Wieser',
    'Schiller',
];

const ADRESSEN = [
    // Wien
    { bundesland: 'W', street: 'Mariahilfer Straße', plz: '1060', city: 'Wien' },
    { bundesland: 'W', street: 'Währinger Straße', plz: '1180', city: 'Wien' },
    { bundesland: 'W', street: 'Simmeringer Hauptstraße', plz: '1110', city: 'Wien' },
    { bundesland: 'W', street: 'Floridsdorfer Hauptstraße', plz: '1210', city: 'Wien' },
    // Niederösterreich
    { bundesland: 'NOE', street: 'Kremser Gasse', plz: '3500', city: 'Krems an der Donau' },
    { bundesland: 'NOE', street: 'Rathausplatz', plz: '3100', city: 'St. Pölten' },
    { bundesland: 'NOE', street: 'Hauptstraße', plz: '2700', city: 'Wiener Neustadt' },
    { bundesland: 'NOE', street: 'Badener Straße', plz: '2380', city: 'Perchtoldsdorf' },
    // Oberösterreich
    { bundesland: 'OOE', street: 'Landstraße', plz: '4020', city: 'Linz' },
    { bundesland: 'OOE', street: 'Stadtplatz', plz: '4600', city: 'Wels' },
    { bundesland: 'OOE', street: 'Stadtplatz', plz: '4400', city: 'Steyr' },
    { bundesland: 'OOE', street: 'Bahnhofstraße', plz: '4210', city: 'Gallneukirchen' },
    // Salzburg
    { bundesland: 'S', street: 'Linzer Bundesstraße', plz: '5023', city: 'Salzburg' },
    { bundesland: 'S', street: 'Münchner Bundesstraße', plz: '5020', city: 'Salzburg' },
    { bundesland: 'S', street: 'Alpenstraße', plz: '5020', city: 'Salzburg' },
    { bundesland: 'S', street: 'Marktplatz', plz: '5400', city: 'Hallein' },
    { bundesland: 'S', street: 'Bahnhofstraße', plz: '5500', city: 'Bischofshofen' },
    // Steiermark
    { bundesland: 'ST', street: 'Herrengasse', plz: '8010', city: 'Graz' },
    { bundesland: 'ST', street: 'Annenstraße', plz: '8020', city: 'Graz' },
    { bundesland: 'ST', street: 'Hauptplatz', plz: '8530', city: 'Deutschlandsberg' },
    { bundesland: 'ST', street: 'Hauptstraße', plz: '8605', city: 'Kapfenberg' },
    // Tirol
    { bundesland: 'T', street: 'Maria-Theresien-Straße', plz: '6020', city: 'Innsbruck' },
    { bundesland: 'T', street: 'Stadtplatz', plz: '6130', city: 'Schwaz' },
    { bundesland: 'T', street: 'Unterer Stadtplatz', plz: '6330', city: 'Kufstein' },
    { bundesland: 'T', street: 'Bahnhofstraße', plz: '6300', city: 'Wörgl' },
    // Vorarlberg
    { bundesland: 'V', street: 'Rathausstraße', plz: '6900', city: 'Bregenz' },
    { bundesland: 'V', street: 'Marktstraße', plz: '6850', city: 'Dornbirn' },
    { bundesland: 'V', street: 'Marktgasse', plz: '6800', city: 'Feldkirch' },
    { bundesland: 'V', street: 'Hauptstraße', plz: '6830', city: 'Rankweil' },
    // Kärnten
    { bundesland: 'K', street: 'Alter Platz', plz: '9020', city: 'Klagenfurt am Wörthersee' },
    { bundesland: 'K', street: 'Hauptplatz', plz: '9500', city: 'Villach' },
    { bundesland: 'K', street: 'Hauptstraße', plz: '9400', city: 'Wolfsberg' },
    { bundesland: 'K', street: 'Bahnhofstraße', plz: '9300', city: 'St. Veit an der Glan' },
    // Burgenland
    { bundesland: 'B', street: 'Esterházystraße', plz: '7000', city: 'Eisenstadt' },
    { bundesland: 'B', street: 'Hauptstraße', plz: '7400', city: 'Oberwart' },
    { bundesland: 'B', street: 'Hauptplatz', plz: '7350', city: 'Oberpullendorf' },
    { bundesland: 'B', street: 'Hauptstraße', plz: '7100', city: 'Neusiedl am See' },
];

function pick(arr) {
    return arr[Math.floor(Math.random() * arr.length)];
}

function randInt(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

// Österreichische SVNR — 10-stellig:
//   Stelle 1-3: laufende Nummer
//   Stelle 4:   Prüfziffer
//   Stelle 5-10: Geburtsdatum DDMMYY
// Prüfziffer = ( Σ (Ziffer_i · Gewicht_i) ) mod 11 über die 9 Ziffern (alle außer der Prüfziffer selbst),
// Gewichte in Reihenfolge: 3, 7, 9, 5, 8, 4, 2, 1, 6.
// Ergebnis 10 → Nummer ist ungültig, dann neu würfeln.
// Verifiziert anhand Referenz-XML (SVNR 4160270104 → check digit 0, geboren 27.01.2004).
function computeSvnrCheckDigit(lfd3, dd, mm, yy) {
    const digits = [+lfd3[0], +lfd3[1], +lfd3[2], +dd[0], +dd[1], +mm[0], +mm[1], +yy[0], +yy[1]];
    const weights = [3, 7, 9, 5, 8, 4, 2, 1, 6];
    const sum = digits.reduce((acc, d, i) => acc + d * weights[i], 0);
    return sum % 11;
}

function randomBirthDate(minYear = 1925, maxYear = 2010) {
    const year = randInt(minYear, maxYear);
    const month = randInt(1, 12);
    // Tagesgrenze grob — 28 reicht (kein Dezember-31-Problem)
    const day = randInt(1, 28);
    return new Date(year, month - 1, day);
}

function formatBirthDateForInput(d) {
    const pad = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function generateSvnr(birthDate) {
    const pad = (n) => String(n).padStart(2, '0');
    const dd = pad(birthDate.getDate());
    const mm = pad(birthDate.getMonth() + 1);
    const yy = pad(birthDate.getFullYear() % 100);
    for (let attempts = 0; attempts < 50; attempts++) {
        const lfd = String(randInt(100, 999));
        const check = computeSvnrCheckDigit(lfd, dd, mm, yy);
        if (check < 10) {
            return `${lfd}${check}${dd}${mm}${yy}`;
        }
    }
    return `100${0}${dd}${mm}${yy}`;
}

export function generateRandomPatient(bundesland = null) {
    const gender = Math.random() < 0.5 ? 'M' : 'F';
    const givenName = pick(gender === 'M' ? VORNAMEN_M : VORNAMEN_F);
    const familyName = pick(NACHNAMEN);
    const birthDate = randomBirthDate();
    const svnr = generateSvnr(birthDate);
    const pool = bundesland ? ADRESSEN.filter((a) => a.bundesland === bundesland) : ADRESSEN;
    const adr = pick(pool.length ? pool : ADRESSEN);
    const houseNumber = String(randInt(1, 120));
    const phoneArea = pick(['0664', '0660', '0676', '0681', '0699']);
    const phoneRest = String(randInt(1000000, 9999999));
    return {
        givenName,
        familyName,
        gender,
        birthDate: formatBirthDateForInput(birthDate),
        svnr,
        address: {
            street: adr.street,
            houseNumber,
            postalCode: adr.plz,
            city: adr.city,
            country: 'A',
        },
        phone: `${phoneArea}${phoneRest}`,
        patientId: String(randInt(1000000, 9999999)),
    };
}

export function generateRandomDoctor() {
    const gender = Math.random() < 0.5 ? 'M' : 'F';
    const givenName = pick(gender === 'M' ? VORNAMEN_M : VORNAMEN_F);
    const familyName = pick(NACHNAMEN);
    const title = pick(['Dr.', 'Dr.', 'Dr.', 'Dr. med.', 'Univ.-Prof. Dr.', 'OA Dr.', 'Prim. Dr.']);
    return { title, givenName, familyName };
}

// Externe Validierung (z.B. für Tests / Eingabe-Validierung)
export function isValidSvnr(svnr) {
    if (!/^\d{10}$/.test(svnr)) return false;
    const lfd = svnr.slice(0, 3);
    const check = +svnr[3];
    const dd = svnr.slice(4, 6);
    const mm = svnr.slice(6, 8);
    const yy = svnr.slice(8, 10);
    return computeSvnrCheckDigit(lfd, dd, mm, yy) === check;
}
