"""Casi di prova del capitolo 11 (TC-01 … TC-18, TC-24) sul motore di calcolo. python3 -m unittest -v"""
import unittest
from decimal import Decimal

from calcolo import calcola, carica_regole, tronca_euro

REGOLE = carica_regole("2025")

DICHIARAZIONI = {k: "true" for k in ("dichR02", "dichR03", "dichR05", "dichR06", "dichR07", "dichR08", "dichR09",
                                     "dichR10", "dichR11", "dichR12", "dichR15", "dichR16", "dichR17", "dichR18",
                                     "nessunaSpesaNonAmmessa")}


def domanda(**kw):
    """Impresa manifatturiera di base, iscritta nel 2010, rapporto INAIL, dichiarazioni spuntate."""
    d = {"annualita": "2025", "ateco": "25.11", "tipoSoggetto": "Impresa", "rapportoAssicurativo": "INAIL",
         "dataIscrizioneCciaa": "2010-03-01", "dataAssociazionePortale": "2026-01-10", "regione": "Toscana",
         "ula": "8", "fatturato": "1500000", "dipendentiImpresa": "8", "dipendentiUnita": "8",
         "partiSociali": "Nessuna", "finanziamentiPrecedenti": "Nessuno"}
    d.update(DICHIARAZIONI)
    d.update(kw)
    return d


class CasiDiProva(unittest.TestCase):

    def test_tc01_asse2_sopra_soglia_con_anticipo(self):
        r = calcola(domanda(asse="2 Rischi infortunistici", tipologia2="a) Ancoraggi", tassoMedio="60",
                            partiSociali="Ente bilaterale o Organismo paritetico", rls="true", certIso45001="true",
                            speseProgetto="100000", speseTecniche="8000", anticipo="true"), REGOLE)
        self.assertEqual(r.punteggio, 201)
        self.assertEqual(r.importi["spesaAmmissibile"], Decimal("108000.00"))
        self.assertEqual(r.importi["contributoTotale"], Decimal("70200.00"))
        self.assertEqual(r.importi["anticipo"], Decimal("35100.00"))
        self.assertEqual(r.importi["fideiussione"], Decimal("38610.00"))
        self.assertTrue(r.registrabile, r.errori)

    def test_tc02_sotto_soglia(self):
        r = calcola(domanda(asse="1.1", ula="60", fatturato="40000000", dipendentiImpresa="60", tassoMedio="10",
                            dataIscrizioneCciaa="2005-01-01", tipologia11="b", speseProgetto="100000"), REGOLE)
        self.assertEqual(r.punteggio, 89)
        self.assertFalse(r.registrabile)
        self.assertEqual(r.errori[0], "Punteggio 89 inferiore alla soglia di 130")

    def test_tc03_riduzione_fatturato(self):
        r = calcola(domanda(asse="2", tipologia2="a", fatturato="3000000"), REGOLE)
        self.assertIn("Dimensioni: 30", r.dettaglioPunteggio[0])

    def test_tc04_contributo_sotto_minimo(self):
        r = calcola(domanda(asse="3", tipologia3="b", speseProgetto="6000", tassoMedio="100", rls="true",
                            partiSociali="Due o più parti sociali"), REGOLE)
        self.assertEqual(r.importi["contributoTotale"], Decimal("3900.00"))
        self.assertIn("inferiore al minimo", " ".join(r.errori))

    def test_tc05_asse12_senza_minimo(self):
        r = calcola(domanda(asse="1.2", ateco="47.11", ula="20", dipendentiImpresa="20", dipendentiUnita="20",
                            tipologia12="a", tassoMedio="100", rls="true", partiSociali="Due o più parti sociali",
                            speseProgetto="5000"), REGOLE)
        self.assertEqual(r.importi["contributoTotale"], Decimal("4000.00"))
        self.assertNotIn("inferiore al minimo", " ".join(r.errori))

    def test_tc06_massimale_e_aggiuntivo(self):
        r = calcola(domanda(asse="1.1", tipologia11="d", speseProgetto="190000", aggiuntivo11="SGSL ISO 45001",
                            speseAggiuntivo="15000"), REGOLE)
        self.assertEqual(r.importi["contributoPrincipale"], Decimal("123500.00"))
        self.assertEqual(r.importi["contributoAggiuntivo"], Decimal("6500.00"))
        self.assertEqual(r.importi["contributoTotale"], Decimal("130000.00"))

    def test_tc07_contributo_oltre_massimo(self):
        r = calcola(domanda(asse="2", tipologia2="a", speseProgetto="250000"), REGOLE)
        self.assertEqual(r.importi["contributoTotale"], Decimal("130000"))
        self.assertTrue(any("130.000,00 €" in a for a in r.avvisi))

    def test_tc08_macchina_oltre_80_listino(self):
        r = calcola(domanda(asse="2", tipologia2="b", macchina1Preventivo="50000", macchina1Listino="55000"), REGOLE)
        self.assertEqual(r.importi["spesaAmmissibile"], Decimal("44000.00"))

    def test_tc09_spese_tecniche_oltre_10(self):
        r = calcola(domanda(asse="2", tipologia2="a", speseProgetto="60000", speseTecniche="9000"), REGOLE)
        self.assertEqual(r.importi["spesaAmmissibile"], Decimal("66000.00"))

    def test_tc10_fascia_ula_al_confine(self):
        r = calcola(domanda(asse="2", tipologia2="a", ula="10.44"), REGOLE)
        self.assertEqual(r.ula, Decimal("10.4"))
        self.assertIn("Dimensioni: 45", r.dettaglioPunteggio[0])
        r = calcola(domanda(asse="2", tipologia2="a", ula="10,0"), REGOLE)
        self.assertIn("Dimensioni: 50", r.dettaglioPunteggio[0])

    def test_tc11_agricola_primaria(self):
        r = calcola(domanda(ateco="01.11", agricolaPrimaria="true", rapportoAssicurativo="INPS gestione agricola",
                            ula="3", fatturato="300000"), REGOLE)
        self.assertEqual(r.assiAmmessi, ["3", "5.1", "5.2"])
        self.assertIn("R-DEST-03", r.assiNonAmmessi["1.1"])

    def test_tc12_asse4_ateco(self):
        r = calcola(domanda(ateco="25.11"), REGOLE)
        self.assertNotIn("4", r.assiAmmessi)
        self.assertIn("R-DEST-05", r.assiNonAmmessi["4"])
        r = calcola(domanda(ateco="16.23", asse="4", tipologia4a="true"), REGOLE)
        self.assertIn("4", r.assiAmmessi)
        self.assertIn("Rischiosità: 40", " ".join(r.dettaglioPunteggio))

    def test_tc13_asse4_piu_tipologie(self):
        r = calcola(domanda(ateco="16.23", asse="4", tipologia4a="true", tipologia4c="true"), REGOLE)
        self.assertIn("Tipologia: 80", " ".join(r.dettaglioPunteggio))

    def test_tc14_incompatibilita_iso45001(self):
        r = calcola(domanda(asse="1.1", tipologia11="a", aggiuntivo11="SGSL ISO 45001", certIso45001="true"), REGOLE)
        self.assertNotIn("ISO 45001:2023: 5", " ".join(r.dettaglioPunteggio))
        self.assertTrue(any("R-ALL-06" in a for a in r.avvisi))

    def test_tc15_asse5_due_beni(self):
        # Nota: la specifica dà al bene 2 120 punti, ma i criteri comuni (parti sociali 18) valgono per ogni bene:
        # 60 + 60 + 18 = 138. Si verifica la regola come scritta in 9.4 (punto aperto Q3 dell'analisi).
        r = calcola(domanda(ateco="01.11", agricolaPrimaria="true", rapportoAssicurativo="INPS gestione agricola",
                            ula="3", fatturato="300000", asse="5.1", partiSociali="Ente bilaterale o Organismo paritetico",
                            bene1Tipo="Trattore", bene1Misura="2a", bene1Fattore="1a", bene1Preventivo="40000",
                            bene1Modalita="Rottamazione", bene1AnnoSostituito="2008",
                            secondoBene="true", bene2Tipo="Macchina con motore", bene2Misura="2f", bene2Fattore="3a",
                            bene2Preventivo="20000"), REGOLE)
        self.assertEqual(r.punteggioBeni, [Decimal(158), Decimal(138)])
        self.assertTrue(r.registrabile, r.errori)
        self.assertEqual(r.importi["contributoTotale"], Decimal("39000.00"))  # 65% di 40.000 + 20.000

    def test_tc15_bis_bene_sotto_soglia_escluso(self):
        r = calcola(domanda(ateco="01.11", agricolaPrimaria="true", rapportoAssicurativo="INPS gestione agricola",
                            ula="3", fatturato="300000", asse="5.1",
                            bene1Tipo="Trattore", bene1Misura="2a", bene1Fattore="1a", bene1Preventivo="40000",
                            bene1Modalita="Rottamazione", bene1AnnoSostituito="2008",
                            secondoBene="true", bene2Tipo="Macchina senza motore", bene2Misura="2f", bene2Fattore="3a",
                            bene2Preventivo="20000"), REGOLE)
        self.assertEqual(r.punteggioBeni, [Decimal(140), Decimal(120)])
        self.assertTrue(r.registrabile, r.errori)
        self.assertEqual(r.importi["contributoTotale"], Decimal("26000.00"))

    def test_tc16_asse52_requisito_mancante(self):
        r = calcola(domanda(ateco="01.11", agricolaPrimaria="true", rapportoAssicurativo="INPS gestione agricola",
                            ula="3", fatturato="300000", asse="5.2", giovaneEta="true", giovaneCapoAzienda="true",
                            bene1Tipo="Trattore", bene1Misura="2a", bene1Fattore="1a", bene1Preventivo="40000"), REGOLE)
        self.assertEqual(r.importi["contributoTotale"], Decimal("26000.00"))  # 65%
        self.assertTrue(any("R-CALC-06" in a for a in r.avvisi))

    def test_tc17_permuta(self):
        r = calcola(domanda(ateco="01.11", agricolaPrimaria="true", rapportoAssicurativo="INPS gestione agricola",
                            ula="3", fatturato="300000", asse="5.1", bene1Tipo="Trattore", bene1Misura="2a",
                            bene1Fattore="2a", bene1Preventivo="40000", bene1Modalita="Permuta",
                            bene1RicavoPermuta="18000", bene1AnnoSostituito="2008", rls="true"), REGOLE)
        self.assertEqual(r.importi["contributoTotale"], Decimal("22000.00"))

    def test_tc18_anticipo_al_confine(self):
        # 30.000 € esatti: spesa 46.153,85 × 65% = 30.000,00
        r = calcola(domanda(asse="2", tipologia2="a", speseProgetto="46153.85", anticipo="true"), REGOLE)
        self.assertEqual(r.importi["contributoTotale"], Decimal("30000.00"))
        self.assertEqual(r.importi["anticipo"], Decimal("15000.00"))
        r = calcola(domanda(asse="2", tipologia2="a", speseProgetto="46152.31", anticipo="true"), REGOLE)
        self.assertEqual(r.importi["contributoTotale"], Decimal("29999.00"))
        self.assertEqual(r.importi["anticipo"], Decimal("0"))

    def test_registrazione_al_portale_tardiva(self):
        r = calcola(domanda(asse="2", tipologia2="a", dataAssociazionePortale="2026-05-27", speseProgetto="100000"), REGOLE)
        self.assertIn("Registrazione al portale avvenuta oltre il termine previsto dall'art. 12 dell'Avviso", r.errori)

    def test_dichiarazione_mancante(self):
        d = domanda(asse="2", tipologia2="a", speseProgetto="100000")
        d.pop("dichR18")
        self.assertIn("Manca la dichiarazione R-REQ-18 (passo 7)", calcola(d, REGOLE).errori)

    def test_ets_runts(self):
        r = calcola(domanda(tipoSoggetto="ETS iscritto al RUNTS"), REGOLE)
        self.assertEqual(r.assiAmmessi, ["1.1"])

    def test_date_dai_connettori(self):
        r = calcola(domanda(asse="2", tipologia2="a", dataIscrizioneCciaa="Thu Jan 04 00:00:00 CET 2024"), REGOLE)
        self.assertIn("Rischiosità: 4 (iscritta alla CCIAA dal 31/12/2023)", " ".join(r.dettaglioPunteggio))

    def test_importo_troncato(self):
        self.assertEqual(tronca_euro(Decimal("70200.99")), 70200)

    def test_testi_per_orditogram(self):
        t = calcola(domanda(asse="2", tipologia2="a", tassoMedio="60", speseProgetto="100000",
                            speseTecniche="8000"), REGOLE).in_testo(REGOLE)
        self.assertEqual(t["contributoTotaleTesto"], "70.200,00 €")
        self.assertEqual(t["registrabile"], "Sì")


if __name__ == "__main__":
    unittest.main()
