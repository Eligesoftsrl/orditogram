"""Casi di prova del capitolo 11 sui registri del bando (TC-19 … TC-24). python3 -m unittest -v"""
import json
import threading
import unittest
from datetime import date
from http.server import ThreadingHTTPServer
from urllib.request import Request, urlopen

import servizio
from calcolo import carica_regole
from test_calcolo import domanda

REGOLE = carica_regole("2025")


def registra(reg, cf, **kw):
    d = dict(codiceFiscaleImpresa=cf, asse="2", tipologia2="a", tassoMedio="60",
             partiSociali="Due o più parti sociali", rls="true")
    d.update(kw)
    return reg.registra(domanda(**d))


class Registri(unittest.TestCase):
    def setUp(self):
        self.reg = servizio.Registro(":memory:", REGOLE)

    def test_registrazione_e_fotografia(self):
        r = registra(self.reg, "01234560789", speseProgetto="100000", regione="Toscana")
        self.assertEqual(r["esitoRegistrazione"], "REGISTRATA")
        self.assertEqual(r["gruppoSede"], "sede-toscana")
        row = self.reg._domanda(r["numeroDomanda"])
        self.assertEqual(row["versione_regole"], "2025.1")
        self.assertEqual(len(row["hash"]), 64)

    def test_non_registrabile_sotto_soglia(self):
        r = self.reg.registra(domanda(codiceFiscaleImpresa="X", asse="1.1", tipologia11="b", ula="60",
                                      fatturato="40000000", tassoMedio="10"))
        self.assertEqual(r["esitoRegistrazione"], "NON_REGISTRABILE")

    def test_tc19_una_sola_domanda(self):
        a = registra(self.reg, "01234560789", speseProgetto="100000")
        b = registra(self.reg, "01234560789", speseProgetto="120000")
        self.assertEqual(a["esitoRegistrazione"], "REGISTRATA")
        self.assertEqual(b["esitoRegistrazione"], "GIA_REGISTRATA")
        self.assertEqual(b["messaggioRegistrazione"], "L'impresa ha già registrato una domanda per questo Avviso")

    def test_tc19_registrazioni_concorrenti(self):
        esiti = []
        th = [threading.Thread(target=lambda: esiti.append(registra(self.reg, "09999999999", speseProgetto="100000")
                                                            ["esitoRegistrazione"])) for _ in range(8)]
        [t.start() for t in th]
        [t.join() for t in th]
        self.assertEqual(esiti.count("REGISTRATA"), 1)

    def test_codice_non_ambiguo(self):
        n = registra(self.reg, "01234560789", speseProgetto="100000")["numeroDomanda"]
        c = self.reg.codice(n)["codiceIdentificativo"]
        self.assertEqual(len(c), 12)
        self.assertFalse(set(c) & set("0O1I"))
        self.assertEqual(self.reg.codice(n)["codiceIdentificativo"], c)  # si rivede finché non è usato

    def test_tc20_sportello_e_tc21_codice_usato(self):
        ore = iter([1000120, 1000050, 1000300])
        numeri = []
        for cf, spese in (("A1", "92307.69"), ("A2", "76923.08"), ("A3", "61538.46")):  # 60.000, 50.000, 40.000 €
            r = registra(self.reg, cf, speseProgetto=spese, regione="Campania")
            numeri.append((r["numeroDomanda"], self.reg.codice(r["numeroDomanda"])["codiceIdentificativo"]))
        orig = servizio.adesso_ms
        try:
            for (n, c) in numeri:
                servizio.adesso_ms = lambda: next(ore)
                self.assertEqual(self.reg.sportello(n, c)["esitoInvio"], "INVIATA")
        finally:
            servizio.adesso_ms = orig
        # TC-21: secondo invio rifiutato senza dire perché
        self.assertEqual(self.reg.sportello(*numeri[0])["messaggioInvio"], "Codice non valido o già utilizzato")
        self.assertEqual(self.reg.sportello(numeri[0][0], "ZZZZZZZZZZZZ")["esitoInvio"], "RIFIUTATO")
        righe = {x["numero"]: x for x in self.reg.calcola_elenco("PROVVISORIO")}
        self.assertEqual([righe[n]["posizione"] for n, _ in numeri], [2, 1, 3])
        self.assertEqual([righe[n]["esito"] for n, _ in numeri],
                         ["NON_AMMISSIBILE_FONDI", "IN_POSIZIONE_UTILE", "IN_POSIZIONE_UTILE"])
        self.assertEqual([righe[n]["importo"] for n, _ in numeri], [60000, 50000, 40000])
        # redistribuzione: decade quella da 50.000 €, il residuo (60.000 €) basta per quella da 60.000 €
        self.reg.pubblica("PROVVISORIO")
        self.reg.cambia_stato(numeri[1][0], "DECADUTA")
        definitivo = {x["numero"]: x["esito"] for x in self.reg.calcola_elenco("DEFINITIVO")}
        self.assertEqual(definitivo[numeri[0][0]], "SUBENTRATA")
        self.assertEqual(definitivo[numeri[1][0]], "DECADUTA")
        self.assertEqual(definitivo[numeri[2][0]], "AMMISSIBILE")

    def test_tc22_elenco_ncd(self):
        for cf, spese in (("B1", "61538.46"), ("B2", "61538.46")):  # 2 × 40.000 € = 80.000 su 2.500.000
            registra(self.reg, cf, speseProgetto=spese, regione="Campania", asse="3", tipologia3="b")
        righe = self.reg.calcola_elenco("NCD")
        self.assertEqual({x["esito"] for x in righe}, {"NCD"})
        self.assertEqual(len(righe), 2)

    def test_tc23_decadenza(self):
        n = registra(self.reg, "C1", speseProgetto="100000")["numeroDomanda"]
        self.assertEqual(self.reg.cambia_stato(n, "DECADUTA")["statoDomanda"], "DECADUTA")

    def test_tc24_tecnico_non_ammesso(self):
        r = servizio.incarico_tecnico({"cfTecnico": "RSSMRA70A01D612X", "cfLegaleRappresentante": "rssmra70a01d612x"})
        self.assertEqual(r["incaricoAmmesso"], "No")
        r = servizio.incarico_tecnico({"cfTecnico": "VRDLGU75B02H501Y", "cfLegaleRappresentante": "RSSMRA70A01D612X",
                                       "cfSoci": "BNCGNN80C03F205Z, VRDLGU75B02H501Y"})
        self.assertEqual(r["incaricoAmmesso"], "No")
        r = servizio.incarico_tecnico({"cfTecnico": "VRDLGU75B02H501Y", "cfLegaleRappresentante": "RSSMRA70A01D612X"})
        self.assertEqual(r["incaricoAmmesso"], "Sì")

    def test_istruttoria_sospesa(self):
        giorni = iter([date(2026, 9, 1), date(2026, 10, 1), date(2026, 10, 21)])
        self.reg.oggi = lambda: next(giorni)
        self.assertEqual(self.reg.istruttoria("N1", "avvia")["scadenzaIstruttoria"], "2026-12-30T18:00:00")
        self.reg.istruttoria("N1", "sospendi")
        r = self.reg.istruttoria("N1", "riprendi")
        self.assertEqual(r["giorniSospesi"], "20")
        self.assertEqual(r["scadenzaIstruttoria"], "2027-01-19T18:00:00")


class Http(unittest.TestCase):
    def test_calcola_via_http(self):
        reg = servizio.Registro(":memory:", REGOLE)
        srv = ThreadingHTTPServer(("127.0.0.1", 0), servizio.crea_gestore(reg))
        threading.Thread(target=srv.serve_forever, daemon=True).start()
        try:
            url = f"http://127.0.0.1:{srv.server_port}"
            corpo = json.dumps(domanda(asse="2", tipologia2="a", tassoMedio="60", speseProgetto="100000",
                                       speseTecniche="8000")).encode()
            with urlopen(Request(url + "/calcola", data=corpo, method="POST")) as r:
                out = json.loads(r.read())
            self.assertEqual(out["contributoTotaleTesto"], "70.200,00 €")
            with urlopen(url + "/impresa?codiceFiscale=01234560789") as r:
                self.assertEqual(json.loads(r.read())["ragioneSociale"], "Carpenterie Rossi S.r.l.")
        finally:
            srv.shutdown()



class Finestra(unittest.TestCase):
    def test_sportello_chiuso(self):
        reg = servizio.Registro(":memory:", REGOLE, calendario_libero=False)
        n = registra(reg, "D1", speseProgetto="100000")["numeroDomanda"]
        c = reg.codice(n)["codiceIdentificativo"]
        r = reg.sportello(n, c)
        self.assertEqual(r["esitoInvio"], "RIFIUTATO")
        self.assertIn("Sportello chiuso", r["messaggioInvio"])


if __name__ == "__main__":
    unittest.main()
