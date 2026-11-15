package com.eligesoft.orditogram;

import com.eligesoft.orditogram.connettore.ConnettoreOrditogram;
import org.activiti.api.process.model.IntegrationContext;
import org.activiti.api.process.runtime.connector.Connector;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Connettore di esempio del processo "Hello World": legge il dato "nome" e produce "saluto".
 * Utile come modello per scrivere nuovi connettori.
 */
@Component("greetingConnector")
public class GreetingConnector implements Connector, ConnettoreOrditogram {

    @Override
    public IntegrationContext apply(IntegrationContext ctx) {
        Object nome = ctx.getInBoundVariables().get("nome");
        ctx.addOutBoundVariable("saluto", "Ciao, " + nome + "! Benvenuto nel mondo BPMN.");
        return ctx;
    }

    @Override public String id() { return "greetingConnector"; }
    @Override public String nome() { return "Saluto di esempio"; }
    @Override public String descrizione() { return "Legge il dato \"nome\" e scrive un saluto nel dato \"saluto\". Serve come esempio per gli sviluppatori."; }
    @Override public List<Parametro> parametri() { return List.of(); }
}
