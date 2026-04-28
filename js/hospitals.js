// Kuratierte Liste österreichischer Krankenhäuser, gegliedert nach Bundesland.
// Dient dem Bundesland-Quick-Select im Krankenhaus-Formular.

export const HOSPITALS_BY_BUNDESLAND = {
    W: [
        { name: 'AKH Wien — Allgemeines Krankenhaus der Stadt Wien', phone: '+43 1 40400 0', street: 'Währinger Gürtel', houseNumber: '18-20', postalCode: '1090', city: 'Wien' },
        { name: 'Klinik Floridsdorf', phone: '+43 1 27700 0', street: 'Brünner Straße', houseNumber: '68', postalCode: '1210', city: 'Wien' },
        { name: 'Klinik Favoriten', phone: '+43 1 60191 0', street: 'Kundratstraße', houseNumber: '3', postalCode: '1100', city: 'Wien' },
        { name: 'Krankenhaus Hietzing', phone: '+43 1 80110 0', street: 'Wolkersbergenstraße', houseNumber: '1', postalCode: '1130', city: 'Wien' },
        { name: 'Krankenhaus der Barmherzigen Brüder Wien', phone: '+43 1 211 21 0', street: 'Große Mohrengasse', houseNumber: '9', postalCode: '1020', city: 'Wien' },
    ],
    NOE: [
        { name: 'Universitätsklinikum St. Pölten', phone: '+43 2742 9009 0', street: 'Dunant-Platz', houseNumber: '1', postalCode: '3100', city: 'St. Pölten' },
        { name: 'Landesklinikum Wiener Neustadt', phone: '+43 2622 321 0', street: 'Corvinusring', houseNumber: '3-5', postalCode: '2700', city: 'Wiener Neustadt' },
        { name: 'Landesklinikum Amstetten', phone: '+43 7472 604 0', street: 'Ybbsstraße', houseNumber: '8', postalCode: '3300', city: 'Amstetten' },
        { name: 'Landesklinikum Krems', phone: '+43 2732 9004 0', street: 'Mitterweg', houseNumber: '10', postalCode: '3500', city: 'Krems an der Donau' },
    ],
    OOE: [
        { name: 'Kepler Universitätsklinikum Linz', phone: '+43 5 7680 81', street: 'Krankenhausstraße', houseNumber: '9', postalCode: '4021', city: 'Linz' },
        { name: 'Ordensklinikum Linz Barmherzige Schwestern', phone: '+43 732 7677 0', street: 'Seilerstätte', houseNumber: '4', postalCode: '4010', city: 'Linz' },
        { name: 'Salzkammergut Klinikum Vöcklabruck', phone: '+43 5 0554 66', street: 'Dr.-Wilhelm-Bock-Straße', houseNumber: '1', postalCode: '4840', city: 'Vöcklabruck' },
        { name: 'Pyhrn-Eisenwurzen Klinikum Steyr', phone: '+43 5 0554 63', street: 'Sierningerstraße', houseNumber: '170', postalCode: '4400', city: 'Steyr' },
    ],
    S: [
        { name: 'Universitätsklinikum Salzburg Landeskrankenhaus', phone: '+43(0)57255', street: 'Müllner Hauptstraße', houseNumber: '48', postalCode: '5020', city: 'Salzburg' },
        { name: 'Kardinal Schwarzenberg Klinikum', phone: '+43 6452 7110 0', street: 'Kardinal-Schwarzenberg-Straße', houseNumber: '2-6', postalCode: '5620', city: 'Schwarzach im Pongau' },
        { name: 'Tauernklinikum Zell am See', phone: '+43 6542 777 0', street: 'Paracelsusstraße', houseNumber: '8', postalCode: '5700', city: 'Zell am See' },
        { name: 'Klinikum Mittersill', phone: '+43 6562 6161 0', street: 'Felberstraße', houseNumber: '33', postalCode: '5730', city: 'Mittersill' },
    ],
    ST: [
        { name: 'LKH-Univ. Klinikum Graz', phone: '+43 316 385 0', street: 'Auenbruggerplatz', houseNumber: '1', postalCode: '8036', city: 'Graz' },
        { name: 'LKH Hochsteiermark Standort Leoben', phone: '+43 5 7200 0', street: 'Vordernberger Straße', houseNumber: '42', postalCode: '8700', city: 'Leoben' },
        { name: 'Krankenhaus der Elisabethinen Graz', phone: '+43 316 7063 0', street: 'Elisabethinergasse', houseNumber: '14', postalCode: '8020', city: 'Graz' },
        { name: 'LKH Hochsteiermark Standort Bruck an der Mur', phone: '+43 5 7200 0', street: 'Tragösser Straße', houseNumber: '1', postalCode: '8600', city: 'Bruck an der Mur' },
    ],
    T: [
        { name: 'Tirol Kliniken — Universitätsklinik Innsbruck', phone: '+43 5 0504 0', street: 'Anichstraße', houseNumber: '35', postalCode: '6020', city: 'Innsbruck' },
        { name: 'Bezirkskrankenhaus Kufstein', phone: '+43 5372 699 0', street: 'Endach', houseNumber: '27', postalCode: '6330', city: 'Kufstein' },
        { name: 'Bezirkskrankenhaus Schwaz', phone: '+43 5224 602 0', street: 'Swarovskistraße', houseNumber: '1', postalCode: '6130', city: 'Schwaz' },
        { name: 'Krankenhaus St. Vinzenz Zams', phone: '+43 5442 600 0', street: 'Sanatoriumstraße', houseNumber: '43', postalCode: '6511', city: 'Zams' },
    ],
    V: [
        { name: 'Landeskrankenhaus Feldkirch', phone: '+43 5522 303 0', street: 'Carinagasse', houseNumber: '47', postalCode: '6800', city: 'Feldkirch' },
        { name: 'Landeskrankenhaus Bregenz', phone: '+43 5574 401 0', street: 'Karl-Pedenz-Straße', houseNumber: '2', postalCode: '6900', city: 'Bregenz' },
        { name: 'Krankenhaus Dornbirn', phone: '+43 5572 303 0', street: 'Lustenauerstraße', houseNumber: '4', postalCode: '6850', city: 'Dornbirn' },
    ],
    K: [
        { name: 'Klinikum Klagenfurt am Wörthersee', phone: '+43 463 538 0', street: 'Feschnigstraße', houseNumber: '11', postalCode: '9020', city: 'Klagenfurt am Wörthersee' },
        { name: 'LKH Villach', phone: '+43 4242 208 0', street: 'Nikolaigasse', houseNumber: '43', postalCode: '9500', city: 'Villach' },
        { name: 'LKH Wolfsberg', phone: '+43 4352 533 0', street: 'Paulitschgasse', houseNumber: '13', postalCode: '9400', city: 'Wolfsberg' },
    ],
    B: [
        { name: 'Krankenhaus der Barmherzigen Brüder Eisenstadt', phone: '+43 2682 600 0', street: 'Esterházystraße', houseNumber: '26', postalCode: '7000', city: 'Eisenstadt' },
        { name: 'Krankenhaus Oberwart', phone: '+43 3352 404 0', street: 'Dornburggasse', houseNumber: '80', postalCode: '7400', city: 'Oberwart' },
        { name: 'Krankenhaus Güssing', phone: '+43 3322 9300 0', street: 'Haydngasse', houseNumber: '2', postalCode: '7540', city: 'Güssing' },
    ],
};
