/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.svenpvoigt.SemanticFileNaming;
import java.net.URLDecoder;
import java.net.URLEncoder;
import org.apache.jena.reasoner.rulesys.builtins.BaseBuiltin;
import org.apache.jena.graph.*;
import org.apache.jena.reasoner.rulesys.BindingEnvironment;
import org.apache.jena.reasoner.rulesys.BuiltinException;
import org.apache.jena.reasoner.rulesys.RuleContext;
import org.apache.jena.reasoner.rulesys.Util;
import org.apache.jena.vocabulary.*;

/**
 *
 * @author sven
 */

public class RelativeURINode extends BaseBuiltin {
    
    @Override
    public String getName() {
        return "RelativeURINode";
    }

    @Override
    public void headAction(Node[] args, int length, RuleContext context) {
        if (length != 3) {
            throw new BuiltinException(this, context, "Arity must be 3");
        }

        Node relNode = args[0];
        Node literal = args[1];
        Node bindNode = args[2];
        
        Node uriNode = NodeFactory.createURI(relNode.getURI()+literal.getLiteralLexicalForm());
        
        BindingEnvironment env = context.getEnv();
        env.bind(bindNode, uriNode);
    }
}
