// Allgemeine CDA-Bausteine: Header, Patient, Author, Custodian, Brieftext mit Logo.
// Doc-typ-spezifische Sektionen kommen aus doctype-*.js und werden via composeDocument() eingebaut.

import { LOGO_BASE64, LOGO_MIME } from './logo-base64.js';

const HL7_NS = 'urn:hl7-org:v3';
const STYLESHEET_HREF = 'elga-stylesheet-uebung.xsl';

export function escapeXml(str) {
    if (str === null || str === undefined) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&apos;');
}

// Convert "2026-04-26T15:30" or "2026-04-26" → "20260426153000+0100" / "20260426"
export function toHl7Time(value, withTime = true) {
    if (!value) return '';
    const d = new Date(value);
    if (isNaN(d)) return '';
    const pad = (n) => String(n).padStart(2, '0');
    const date = `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}`;
    if (!withTime) return date;
    const time = `${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`;
    const tzMin = -d.getTimezoneOffset();
    const tzSign = tzMin >= 0 ? '+' : '-';
    const tzAbs = Math.abs(tzMin);
    const tz = `${tzSign}${pad(Math.floor(tzAbs / 60))}${pad(tzAbs % 60)}`;
    return `${date}${time}${tz}`;
}

export function uuid() {
    // RFC4122 v4 — keine echte Krypto-Sicherheit nötig, nur Eindeutigkeit
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'
        .replace(/[xy]/g, (c) => {
            const r = (Math.random() * 16) | 0;
            const v = c === 'x' ? r : (r & 0x3) | 0x8;
            return v.toString(16);
        })
        .toUpperCase();
}

function renderAddress(addr) {
    if (!addr) return '';
    return `<addr>
<streetName>${escapeXml(addr.street)}</streetName>
<houseNumber>${escapeXml(addr.houseNumber)}</houseNumber>
<postalCode>${escapeXml(addr.postalCode)}</postalCode>
<city>${escapeXml(addr.city)}</city>
<country>${escapeXml(addr.country || 'A')}</country>
</addr>`;
}

function renderRecordTarget(patient) {
    return `<recordTarget>
<patientRole>
<id extension="${escapeXml(patient.patientId || '0000000')}" root="1.2.40.0.34.99.111.1.1"/>
<id assigningAuthorityName="Österreichische Sozialversicherung" extension="${escapeXml(patient.svnr)}" root="1.2.40.0.10.1.4.3.1"/>
${renderAddress(patient.address)}
<telecom value="tel:${escapeXml(patient.phone || '')}"/>
<patient>
<name>
<given>${escapeXml(patient.givenName)}</given>
<family>${escapeXml(patient.familyName)}</family>
</name>
<administrativeGenderCode code="${escapeXml(patient.gender)}" codeSystem="2.16.840.1.113883.5.1" codeSystemName="HL7:AdministrativeGender" displayName="${patient.gender === 'M' ? 'Male' : 'Female'}"/>
<birthTime value="${toHl7Time(patient.birthDate, false)}"/>
</patient>
</patientRole>
</recordTarget>`;
}

function renderOrganizationBlock(org) {
    return `<id assigningAuthorityName="GDA-Index" root="1.2.40.0.34.3.1.99999"/>
<name>${escapeXml(org.name)}</name>
<telecom value="tel:${escapeXml(org.phone || '')}"/>
${renderAddress(org.address)}`;
}

function renderPersonBlock(person) {
    const prefix = person.title ? `<prefix qualifier="AC">${escapeXml(person.title)}</prefix>` : '';
    return `<name>
${prefix}
<given>${escapeXml(person.givenName)}</given>
<family>${escapeXml(person.familyName)}</family>
</name>`;
}

function renderAuthor(author, organization, time) {
    return `<author>
<time value="${toHl7Time(time)}"/>
<assignedAuthor>
<id root="1.2.40.0.34.99.111.1.3"/>
<telecom value="tel:${escapeXml(organization.phone || '')}"/>
<assignedPerson>
${renderPersonBlock(author)}
</assignedPerson>
<representedOrganization>
${renderOrganizationBlock(organization)}
</representedOrganization>
</assignedAuthor>
</author>`;
}

function renderCustodian(organization) {
    return `<custodian>
<assignedCustodian>
<representedCustodianOrganization>
${renderOrganizationBlock(organization)}
</representedCustodianOrganization>
</assignedCustodian>
</custodian>`;
}

function renderLegalAuthenticator(author, organization, time) {
    return `<legalAuthenticator>
<time value="${toHl7Time(time)}"/>
<signatureCode code="S"/>
<assignedEntity>
<id root="1.2.40.0.34.99.111.1.3"/>
<telecom value="tel:${escapeXml(organization.phone || '')}"/>
<assignedPerson>
${renderPersonBlock(author)}
</assignedPerson>
<representedOrganization>
${renderOrganizationBlock(organization)}
</representedOrganization>
</assignedEntity>
</legalAuthenticator>`;
}

function renderEncompassingEncounter(encounter, organization) {
    if (!encounter || (!encounter.admissionDate && !encounter.dischargeDate)) return '';
    const low = encounter.admissionDate ? `<low value="${toHl7Time(encounter.admissionDate)}"/>` : '';
    const high = encounter.dischargeDate ? `<high value="${toHl7Time(encounter.dischargeDate)}"/>` : '';
    return `<componentOf>
<encompassingEncounter>
<id root="1.2.40.0.34.99.111.1.5" extension="${escapeXml(encounter.caseId || uuid())}"/>
<code code="IMP" codeSystem="2.16.840.1.113883.5.4" codeSystemName="HL7:ActCode" displayName="inpatient encounter"/>
<effectiveTime>
${low}
${high}
</effectiveTime>
<location>
<healthCareFacility>
<id root="1.2.40.0.34.99.111.1.6"/>
<code code="HOSP" codeSystem="2.16.840.1.113883.5.111" codeSystemName="HL7:RoleCode" displayName="Hospital"/>
<location>
<name>${escapeXml(encounter.ward || organization.name)}</name>
</location>
<serviceProviderOrganization>
${renderOrganizationBlock(organization)}
</serviceProviderOrganization>
</healthCareFacility>
</location>
</encompassingEncounter>
</componentOf>`;
}

function formatGermanDateTime(value) {
    if (!value) return '';
    const d = new Date(value);
    if (isNaN(d)) return '';
    const months = [
        'Jänner',
        'Februar',
        'März',
        'April',
        'Mai',
        'Juni',
        'Juli',
        'August',
        'September',
        'Oktober',
        'November',
        'Dezember',
    ];
    const pad = (n) => String(n).padStart(2, '0');
    return `${d.getDate()}. ${months[d.getMonth()]} ${d.getFullYear()} um ${pad(d.getHours())}:${pad(d.getMinutes())} Uhr`;
}

function defaultBrieftext(state) {
    const female = state.patient?.gender === 'F';
    const accusative = female ? 'die oben genannte Patientin' : 'den oben genannten Patienten';
    const relative = female ? 'die sich' : 'der sich';
    const adm = formatGermanDateTime(state.encounter?.admissionDate);
    const dis = formatGermanDateTime(state.encounter?.dischargeDate);
    const stayPart = adm && dis ? `vom ${adm} bis ${dis}` : adm ? `seit ${adm}` : dis ? `bis ${dis}` : '';
    return `Sehr geehrte Frau Kollegin, sehr geehrter Herr Kollege,\n\nwir berichten Ihnen über ${accusative}, ${relative} ${stayPart} in unserer stationären Behandlung befand.`;
}

// Brieftext-Sektion mit eingebettetem Logo (observationMedia)
export function renderBrieftextSection(state) {
    const greeting = state.brieftext?.text || defaultBrieftext(state);
    // CDA narrative: Zeilenumbrüche werden über <br/> oder <paragraph> abgebildet
    const paragraphs = greeting
        .split(/\n\n+/)
        .map((p) => `<paragraph>${escapeXml(p.replace(/\n/g, ' '))}</paragraph>`)
        .join('\n');
    return `<component typeCode="COMP">
<section classCode="DOCSECT">
<templateId root="1.2.40.0.34.11.1.2.1"/>
<code code="BRIEFT" codeSystem="1.2.40.0.34.5.40" codeSystemName="ELGA_Sections" displayName="Brieftext"/>
<title>Brieftext</title>
<text>
${paragraphs}
</text>
<entry>
<observationMedia moodCode="EVN" classCode="OBS">
<templateId root="1.2.40.0.34.11.1.3.2"/>
<value mediaType="${LOGO_MIME}" representation="B64">${LOGO_BASE64}</value>
</observationMedia>
</entry>
</section>
</component>`;
}

// Master assembly. `sections` ist ein Array von rohen <component>-Strings (incl. Brieftext).
export function composeDocument({ documentMeta, patient, author, organization, encounter, sections, legalAuthTime }) {
    const time = documentMeta.effectiveTime || new Date().toISOString();
    return `<?xml version="1.0" encoding="UTF-8"?>
<?xml-stylesheet type="text/xsl" href="${STYLESHEET_HREF}"?>
<ClinicalDocument xmlns="${HL7_NS}" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
<realmCode code="AT"/>
<typeId extension="POCD_HD000040" root="2.16.840.1.113883.1.3"/>
<templateId assigningAuthorityName="ELGA" root="1.2.40.0.34.11.1"/>
<templateId assigningAuthorityName="ELGA" root="1.2.40.0.34.11.2"/>
<id extension="${uuid()}" root="1.2.40.0.34.99.111.1.1"/>
<code code="${escapeXml(documentMeta.code || '11490-0')}" codeSystem="2.16.840.1.113883.6.1" codeSystemName="LOINC" displayName="${escapeXml(documentMeta.codeDisplayName || 'Discharge summarization Note')}"/>
<title>${escapeXml(documentMeta.title || 'Entlassungsbrief')}</title>
<effectiveTime value="${toHl7Time(time)}"/>
<confidentialityCode code="N" codeSystem="2.16.840.1.113883.5.25" codeSystemName="HL7:Confidentiality" displayName="normal"/>
<languageCode code="de-AT"/>
<setId root="${uuid()}"/>
<versionNumber value="1"/>
${renderRecordTarget(patient)}
${renderAuthor(author, organization, time)}
${renderCustodian(organization)}
${renderLegalAuthenticator(author, organization, legalAuthTime || time)}
${renderEncompassingEncounter(encounter, organization)}
<component>
<structuredBody>
${sections.join('\n')}
</structuredBody>
</component>
</ClinicalDocument>`;
}

// Helper für Sektionen mit Tabelle als Narrative
export function renderSection({ templateId, code, codeSystem, codeSystemName, displayName, title, narrative }) {
    return `<component typeCode="COMP">
<section classCode="DOCSECT">
<templateId root="${templateId}"/>
<code code="${escapeXml(code)}" codeSystem="${codeSystem || '2.16.840.1.113883.6.1'}" codeSystemName="${codeSystemName || 'LOINC'}" displayName="${escapeXml(displayName)}"/>
<title>${escapeXml(title)}</title>
<text>
${narrative}
</text>
</section>
</component>`;
}
