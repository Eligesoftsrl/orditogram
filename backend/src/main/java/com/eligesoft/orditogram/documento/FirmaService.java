package com.eligesoft.orditogram.documento;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.cms.Time;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.CMSVerifierCertificateNotValidException;
import org.bouncycastle.cms.SignerId;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.TimeStampToken;
import org.bouncycastle.util.Store;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Provider;
import java.security.PublicKey;
import java.util.*;

/**
 * Verifica tecnica delle firme digitali:
 *  - PAdES: firme dentro il PDF (anche multiple) e marche temporali documentali
 *  - CAdES: busta .p7m (anche annidata p7m dentro p7m), con estrazione del documento originale
 *
 * Per ogni firma controlla l'INTEGRITÀ (il documento non è stato alterato dopo la firma)
 * e la VALIDITÀ DEL CERTIFICATO alla data di firma.
 * NON verifica la catena di fiducia verso gli elenchi AgID/EU né lo stato di revoca:
 * per una validazione legale serve un servizio di verifica qualificato (es. DSS).
 */
@Service
public class FirmaService {

    private static final Provider BC = new BouncyCastleProvider();
    private static final int MAX_ANNIDAMENTO = 5;

    /** Una firma (o marca temporale) trovata nel file. */
    public record Firma(String tipo,              // "firma" | "marca temporale"
                        String firmatario,
                        String codiceFiscale,
                        String emittente,
                        Date data,
                        Boolean integra,
                        Boolean certificatoValido,
                        Date certificatoScadenza,
                        Boolean copreTuttoDocumento,
                        String errore) {}

    /**
     * tipo:  NESSUNA | PADES | CADES | NON_LEGGIBILE
     * esito: NESSUNA | OK | ATTENZIONE | KO
     */
    public record Report(String tipo, String esito, String descrizione, List<Firma> firme,
                         String contenutoNome) {}

    // ======================================================================= API

    public Report analizza(byte[] data, String filename) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".p7m")) return cades(data, filename);
            if (isPdf(data)) return pades(data);
            if (isCms(data)) return cades(data, filename);
            return new Report("NESSUNA", "NESSUNA", "File non firmato", List.of(), null);
        } catch (Exception e) {
            return new Report("NON_LEGGIBILE", "KO",
                "Impossibile verificare la firma: " + messaggio(e), List.of(), null);
        }
    }

    /** Documento originale contenuto in una busta CAdES (anche annidata). */
    public byte[] estraiContenuto(byte[] data) {
        try {
            byte[] inner = data;
            int depth = 0;
            while (isCms(inner) && depth++ < MAX_ANNIDAMENTO) {
                byte[] next = contenuto(new CMSSignedData(derOBase64(inner)));
                if (next == null) throw new IllegalStateException("La busta non contiene il documento (firma detached)");
                inner = next;
            }
            if (depth == 0) throw new IllegalStateException("Il file non è una busta CAdES");
            return inner;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Busta CAdES non leggibile: " + messaggio(e), e);
        }
    }

    /** Nome del documento contenuto: "contratto.pdf.p7m.p7m" -> "contratto.pdf". */
    public static String nomeContenuto(String filename) {
        String n = filename == null ? "documento" : filename;
        while (n.toLowerCase(Locale.ROOT).endsWith(".p7m")) n = n.substring(0, n.length() - 4);
        return n.isBlank() ? "documento" : n;
    }

    // ======================================================================= CAdES

    private Report cades(byte[] data, String filename) throws Exception {
        CMSSignedData cms = new CMSSignedData(derOBase64(data));
        List<Firma> firme = new ArrayList<>(verificaFirme(cms, null, null));
        byte[] inner = contenuto(cms);

        int depth = 0;
        while (inner != null && isCms(inner) && depth++ < MAX_ANNIDAMENTO) {   // p7m dentro p7m
            CMSSignedData nested = new CMSSignedData(derOBase64(inner));
            firme.addAll(verificaFirme(nested, null, null));
            inner = contenuto(nested);
        }

        String esito = esito(firme, false);
        String descrizione = inner == null
            ? "Busta CAdES senza documento incorporato (firma detached)"
            : descrizione("CAdES", firme, esito, false);
        return new Report("CADES", esito, descrizione, firme, inner == null ? null : nomeContenuto(filename));
    }

    private static byte[] contenuto(CMSSignedData cms) {
        CMSTypedData c = cms.getSignedContent();
        return c == null ? null : (byte[]) c.getContent();
    }

    // ======================================================================= PAdES

    private Report pades(byte[] data) throws Exception {
        try (PDDocument doc = caricaPdf(data)) {
            List<PDSignature> sigs = doc.getSignatureDictionaries();
            if (sigs.isEmpty()) return new Report("NESSUNA", "NESSUNA", "PDF non firmato", List.of(), null);

            List<Firma> firme = new ArrayList<>();
            boolean ultimaCopreTutto = false;
            for (PDSignature sig : sigs) {
                int[] br = sig.getByteRange();
                boolean copre = br != null && br.length == 4 && (long) br[2] + br[3] == data.length;
                ultimaCopreTutto |= copre;
                byte[] contents = sig.getContents(data);
                byte[] signed = sig.getSignedContent(data);
                if ("ETSI.RFC3161".equals(sig.getSubFilter())) {
                    firme.add(marcaTemporale(contents, signed, copre));
                } else {
                    Date dataPdf = sig.getSignDate() != null ? sig.getSignDate().getTime() : null;
                    CMSSignedData cms = new CMSSignedData(new CMSProcessableByteArray(signed), contents);
                    firme.addAll(verificaFirme(cms, copre, dataPdf));
                }
            }
            boolean modificato = !ultimaCopreTutto;
            String esito = esito(firme, modificato);
            return new Report("PADES", esito, descrizione("PAdES", firme, esito, modificato), firme, null);
        }
    }

    /** Isolato per compatibilità: PDFBox 3 usa Loader.loadPDF, PDFBox 2 PDDocument.load. */
    protected PDDocument caricaPdf(byte[] data) throws IOException {
        return Loader.loadPDF(data);
    }

    private Firma marcaTemporale(byte[] contents, byte[] signed, boolean copre) {
        try {
            TimeStampToken tst = new TimeStampToken(new CMSSignedData(contents));
            X509CertificateHolder cert = certificato(tst.getCertificates(), tst.getSID());
            DigestCalculator calc = new JcaDigestCalculatorProviderBuilder().setProvider(BC).build()
                .get(tst.getTimeStampInfo().getHashAlgorithm());
            try (OutputStream os = calc.getOutputStream()) { os.write(signed); }
            boolean impronta = Arrays.equals(calc.getDigest(), tst.getTimeStampInfo().getMessageImprintDigest());
            String errore = impronta ? null : "L'impronta della marca non corrisponde al documento";
            boolean firmaTsa = false;
            if (cert != null) {
                try {
                    tst.validate(new JcaSimpleSignerInfoVerifierBuilder().setProvider(BC).build(cert));
                    firmaTsa = true;
                } catch (Exception e) {
                    errore = "Firma della TSA non valida: " + messaggio(e);
                }
            } else {
                errore = "Certificato della TSA non presente";
            }
            Date gen = tst.getTimeStampInfo().getGenTime();
            return new Firma("marca temporale", cert != null ? nome(cert) : "TSA sconosciuta", null,
                cert != null ? emittente(cert) : null, gen, impronta && firmaTsa,
                cert != null ? cert.isValidOn(gen) : null, cert != null ? cert.getNotAfter() : null,
                copre, errore);
        } catch (Exception e) {
            return new Firma("marca temporale", null, null, null, null, false, null, null, copre,
                "Marca temporale non leggibile: " + messaggio(e));
        }
    }

    // ======================================================================= verifica CMS

    private List<Firma> verificaFirme(CMSSignedData cms, Boolean copre, Date dataAlternativa) {
        List<Firma> out = new ArrayList<>();
        Store<X509CertificateHolder> certs = cms.getCertificates();
        for (SignerInformation si : cms.getSignerInfos().getSigners()) {
            X509CertificateHolder cert = certificato(certs, si.getSID());
            Date quando = dataFirma(si);
            if (quando == null) quando = dataAlternativa;

            Boolean integra = null;
            String errore = null;
            if (cert == null) {
                errore = "Certificato del firmatario non presente nella firma";
            } else {
                try {
                    integra = si.verify(new JcaSimpleSignerInfoVerifierBuilder().setProvider(BC).build(cert));
                    if (!integra) errore = "La firma non corrisponde al documento";
                } catch (CMSVerifierCertificateNotValidException e) {
                    // certificato scaduto/non ancora valido alla data di firma: verifico comunque l'integrità
                    try {
                        PublicKey key = new JcaX509CertificateConverter().setProvider(BC).getCertificate(cert).getPublicKey();
                        integra = si.verify(new JcaSimpleSignerInfoVerifierBuilder().setProvider(BC).build(key));
                        errore = "Certificato non valido (scaduto o non ancora valido) alla data di firma";
                    } catch (Exception e2) {
                        integra = false;
                        errore = "La firma non corrisponde al documento: " + messaggio(e2);
                    }
                } catch (Exception e) {
                    integra = false;
                    errore = "La firma non corrisponde al documento: " + messaggio(e);
                }
            }
            Boolean valido = cert == null ? null : cert.isValidOn(quando != null ? quando : new Date());
            out.add(new Firma("firma",
                cert != null ? nome(cert) : null,
                cert != null ? codiceFiscale(cert) : null,
                cert != null ? emittente(cert) : null,
                quando, integra, valido,
                cert != null ? cert.getNotAfter() : null,
                copre, errore));
        }
        return out;
    }

    private static X509CertificateHolder certificato(Store<X509CertificateHolder> certs, SignerId sid) {
        if (certs == null || sid == null) return null;
        Collection<X509CertificateHolder> matches = certs.getMatches(sid);
        return matches.isEmpty() ? null : matches.iterator().next();
    }

    private static Date dataFirma(SignerInformation si) {
        AttributeTable t = si.getSignedAttributes();
        if (t == null) return null;
        Attribute a = t.get(CMSAttributes.signingTime);
        if (a == null || a.getAttrValues().size() == 0) return null;
        try {
            return Time.getInstance(a.getAttrValues().getObjectAt(0)).getDate();
        } catch (Exception e) {
            return null;
        }
    }

    // ======================================================================= esito

    private static String esito(List<Firma> firme, boolean modificatoDopoFirma) {
        if (firme.isEmpty()) return "NESSUNA";
        if (firme.stream().anyMatch(f -> !Boolean.TRUE.equals(f.integra()))) return "KO";
        if (firme.stream().anyMatch(f -> Boolean.FALSE.equals(f.certificatoValido()))) return "KO";
        if (modificatoDopoFirma) return "ATTENZIONE";
        return "OK";
    }

    private static String descrizione(String tipo, List<Firma> firme, String esito, boolean modificato) {
        long n = firme.stream().filter(f -> "firma".equals(f.tipo())).count();
        long marche = firme.size() - n;
        String quante = n == 1 ? "1 firma" : n + " firme";
        if (marche > 0) quante += marche == 1 ? " e 1 marca temporale" : " e " + marche + " marche temporali";
        boolean alterata = firme.stream().anyMatch(f -> !Boolean.TRUE.equals(f.integra()));
        return switch (esito) {
            case "OK" -> tipo + ": " + quante + ", integre";
            case "KO" -> alterata
                ? tipo + ": " + quante + ", almeno una NON corrisponde al documento"
                : tipo + ": " + quante + " integre, ma un certificato non era valido alla data di firma";
            case "ATTENZIONE" -> tipo + ": " + quante + " integre, ma il PDF è stato modificato dopo l'ultima firma";
            default -> tipo + ": nessuna firma";
        };
    }

    // ======================================================================= utilità

    private static boolean isPdf(byte[] d) {
        if (d == null || d.length < 5) return false;
        // "%PDF-" può essere preceduto da qualche byte spurio
        String head = new String(d, 0, Math.min(d.length, 1024), StandardCharsets.ISO_8859_1);
        return head.contains("%PDF-");
    }

    private static boolean isCms(byte[] d) {
        if (d == null || d.length < 16) return false;
        try {
            new CMSSignedData(derOBase64(d));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Le buste .p7m possono essere binarie (DER) o in Base64/PEM. */
    private static byte[] derOBase64(byte[] d) {
        if (d.length > 0 && d[0] == 0x30) return d;
        String s = new String(d, StandardCharsets.US_ASCII)
            .replaceAll("-----(BEGIN|END)[^-]*-----", "")
            .replaceAll("\\s", "");
        return Base64.getDecoder().decode(s);
    }

    private static String nome(X509CertificateHolder c) {
        X500Name s = c.getSubject();
        String cn = rdn(s, BCStyle.CN);
        if (cn != null) return cn;
        String n = Objects.toString(rdn(s, BCStyle.GIVENNAME), "") + " " + Objects.toString(rdn(s, BCStyle.SURNAME), "");
        return n.isBlank() ? s.toString() : n.trim();
    }

    private static String emittente(X509CertificateHolder c) {
        String o = rdn(c.getIssuer(), BCStyle.O);
        return o != null ? o : rdn(c.getIssuer(), BCStyle.CN);
    }

    /** Nei certificati italiani il codice fiscale è nel SERIALNUMBER, es. "TINIT-RSSMRA80A01H501U". */
    private static String codiceFiscale(X509CertificateHolder c) {
        String sn = rdn(c.getSubject(), BCStyle.SERIALNUMBER);
        if (sn == null) return null;
        return sn.replaceFirst("^(TINIT-|IT:)", "");
    }

    private static String rdn(X500Name name, ASN1ObjectIdentifier oid) {
        RDN[] r = name.getRDNs(oid);
        return r.length == 0 ? null : IETFUtils.valueToString(r[0].getFirst().getValue());
    }

    private static String messaggio(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
