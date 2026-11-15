package com.eligesoft.orditogram.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Accesso con un identity provider OIDC (Keycloak, Microsoft Entra ID, ...).
 * I provider si dichiarano con le proprietà standard di Spring (spring.security.oauth2.client.*);
 * qui si dice come leggere utente e gruppi dal token.
 *
 * app:
 *   oidc:
 *     claim-utente: preferred_username     # quale claim diventa il nome utente in Orditogram
 *     claim-gruppi: groups                 # anche annidato: realm_access.roles (Keycloak)
 *     mappa-gruppi:                        # facoltativa: dal valore del token al gruppo di Orditogram
 *       "6f1c2a7e-...": analisti            # (con Entra ID i gruppi arrivano come ID)
 *     gruppo-admin: orditogram-admin             # chi ha questo gruppo nel token è amministratore
 *     sincronizza-gruppi: true             # a ogni accesso i gruppi di Orditogram seguono quelli del token
 *     crea-utenti: true                    # al primo accesso l'utente viene creato in Orditogram
 *     collega-utenti-esistenti: true       # un utente già creato in Orditogram con lo stesso nome viene collegato
 *                                          # al primo accesso (mai gli amministratori locali)
 */
@Component
@ConfigurationProperties(prefix = "app.oidc")
public class OidcProperties {

    private String claimUtente = "preferred_username";
    private String claimGruppi = "groups";
    private Map<String, String> mappaGruppi = new LinkedHashMap<>();
    private String gruppoAdmin;
    private boolean sincronizzaGruppi = true;
    private boolean creaUtenti = true;
    private boolean collegaUtentiEsistenti = true;
    private String dopoLogin = "/";

    public String getClaimUtente() { return claimUtente; }
    public void setClaimUtente(String v) { this.claimUtente = v; }
    public String getClaimGruppi() { return claimGruppi; }
    public void setClaimGruppi(String v) { this.claimGruppi = v; }
    public Map<String, String> getMappaGruppi() { return mappaGruppi; }
    public void setMappaGruppi(Map<String, String> v) { this.mappaGruppi = v; }
    public String getGruppoAdmin() { return gruppoAdmin; }
    public void setGruppoAdmin(String v) { this.gruppoAdmin = v; }
    public boolean isSincronizzaGruppi() { return sincronizzaGruppi; }
    public void setSincronizzaGruppi(boolean v) { this.sincronizzaGruppi = v; }
    public boolean isCreaUtenti() { return creaUtenti; }
    public void setCreaUtenti(boolean v) { this.creaUtenti = v; }
    public boolean isCollegaUtentiEsistenti() { return collegaUtentiEsistenti; }
    public void setCollegaUtentiEsistenti(boolean v) { this.collegaUtentiEsistenti = v; }
    public String getDopoLogin() { return dopoLogin; }
    public void setDopoLogin(String v) { this.dopoLogin = v; }
}
