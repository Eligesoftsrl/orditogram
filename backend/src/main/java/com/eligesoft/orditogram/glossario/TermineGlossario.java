package com.eligesoft.orditogram.glossario;

import jakarta.persistence.*;

/** Un termine del glossario scelto dall'installazione (solo quelli diversi dal predefinito). */
@Entity
@Table(name = "glossario_termine")
public class TermineGlossario {

    @Id
    @Column(length = 40)
    private String chiave;

    @Column(nullable = false, length = 60)
    private String singolare;

    @Column(nullable = false, length = 60)
    private String plurale;

    /** "M" o "F", come testo. */
    @Column(nullable = false, length = 1)
    private String genere;

    protected TermineGlossario() { }

    public TermineGlossario(String chiave, String singolare, String plurale, String genere) {
        this.chiave = chiave;
        this.singolare = singolare;
        this.plurale = plurale;
        this.genere = genere;
    }

    public String getChiave() { return chiave; }
    public String getSingolare() { return singolare; }
    public String getPlurale() { return plurale; }
    public String getGenere() { return genere; }
}
