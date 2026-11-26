package com.eligesoft.orditogram.registro;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Il metodo legge dati di una pratica, di un documento o di una scheda: ogni chiamata lascia una riga nel registro
 * delle letture (chi, cosa, quando, da dove), anche quando l'accesso viene negato. Lo fa {@link AspettoLetture}.
 *
 * <pre>
 * &#64;RegistraLettura(value = "PRATICA_APERTA", oggetto = "pi")
 * public Map&lt;String, Object&gt; dettaglio(&#64;PathVariable String pi) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RegistraLettura {

    /** Il tipo di lettura, es. PRATICA_APERTA, DOCUMENTO_SCARICATO. */
    String value();

    /** Il nome del parametro che identifica ciò che si legge (id della pratica, del documento...). */
    String oggetto() default "";

    /** Il nome di un parametro da scrivere nei dettagli (es. il tipo di anagrafica), facoltativo. */
    String dettagli() default "";
}
