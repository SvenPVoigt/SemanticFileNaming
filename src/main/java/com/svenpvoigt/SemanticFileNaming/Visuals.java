/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.svenpvoigt.SemanticFileNaming;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 *
 * @author sven
 */

class El {
    String elName;
    String id;
    String source;
    String target;
    String highestType;
    String lowestType;
    String label;
    String kind;
    String schemaLevel;
    
    public El(String id) {
        this.elName="el";
        this.id=id;
    }
    
    public Element xml(Document doc) {
        Element el = doc.createElement(this.elName);
        el.setAttribute("id", this.id);
        if (this.source!=null) el.setAttribute("source", this.source);
        if (this.target!=null) el.setAttribute("target", this.target);
        if (this.highestType!=null) {
            Element data = doc.createElement("data");
            data.setAttribute("key", "highestType");
            data.setTextContent(this.highestType);
            el.appendChild(data);
        }
        if (this.lowestType!=null) {
            Element data = doc.createElement("data");
            data.setAttribute("key", "lowestType");
            data.setTextContent(this.lowestType);
            el.appendChild(data);
        }
        if (this.label!=null) {
            Element data = doc.createElement("data");
            data.setAttribute("key", "label");
            data.setTextContent(this.label);
            el.appendChild(data);
        }
        if (this.kind!=null) {
            Element data = doc.createElement("data");
            data.setAttribute("key", "kind");
            data.setTextContent(this.kind);
            el.appendChild(data);
        }
        if (this.schemaLevel!=null) {
            Element data = doc.createElement("data");
            data.setAttribute("key", "schemaLevel");
            data.setTextContent(this.schemaLevel);
            el.appendChild(data);
        }
        
        return el;
    }
}


class NodeEl extends El {
    public NodeEl(String id) {
        super(id);
        this.elName = "node";
        this.id = id;
    }
}


class EdgeEl extends El {
    public EdgeEl(String id) {
        super(id);
        this.elName = "edge";
        this.id = id;
    }
}




public class Visuals {
    static String prefix = "tag:SNF@null.net,2023:";
    static String specPrefix = "example:";
    static List<String> namespaces = new ArrayList();
    static Set<Resource> otherProps = Set.of(RDF.type, RDFS.label);
    static long edgeCount=0;
    static long literalCount=0;
    
    public static void main(String[] args) throws Exception {
        System.out.println(Arrays.asList(args));
        
        specPrefix = args[1];
        Model model = ModelFactory.createDefaultModel();
        model.read( new ByteArrayInputStream(Files.readString(Path.of(args[0])).getBytes()), null, "TTL");
        toGraphML(model, args[0]+".graphml");
    }
    
    static boolean inNamespaces(String uri) {
        for (String ns: namespaces) {
            if (uri.startsWith(ns)) return true;
        }
        
        return false;
    }
    
    static void toGraphML(Model model, String outPath) throws Exception {
//        HashMap<Property, String> predicates = getPredToKeyMap(model);
        
        HashMap<Resource, NodeEl> nodeMap = new HashMap();
        List<NodeEl> litNodeList = new ArrayList();
        List<EdgeEl> edgeList = new ArrayList();
        
        model = model
                .removeAll(null, RDFS.domain, null)
                .removeAll(null, RDFS.range, null)
                .removeAll(null, RDF.type, RDFS.Class)
                .removeAll(null, RDF.type, RDF.Property)
                .removeAll(null, model.getProperty(specPrefix+"suppliedBy"), null)
                .removeAll(null, model.getProperty(specPrefix+"producedBy"), null)
                .removeAll(RDFS.Resource, null, null)
                .removeAll(null, null, RDFS.Resource)
                .removeAll(null, model.getProperty(specPrefix+"involved"), null);
        
        Set<Property> propSet = model.listStatements().mapWith(stmt->stmt.getPredicate()).toSet();
        
        Resource metaclass = model.getResource(specPrefix+"MetaClass");
        
        Set<Resource> metaClassSet = model.listResourcesWithProperty(RDF.type, metaclass).toSet();
        
        Set<Resource> classSet = model.listObjectsOfProperty(RDF.type)
                .mapWith(obj -> {
                    if (obj.canAs(Resource.class)) return obj.as(Resource.class);
                    else System.out.println("Some classes weren't URI Resources");
                    return null;
                }).toSet();
        

        for (StmtIterator stmts = model.listStatements(); stmts.hasNext(); ) {
            Statement st = stmts.next();
            Resource subj = st.getSubject();
            Property pred = st.getPredicate();
            RDFNode obj = st.getObject();
            
            // If predicate is label, continue
            if (pred.equals(RDFS.label)) continue;
            
            // If predicate is type, continue
            // We are only adding type edges to the lowest type
            if (pred.equals(RDF.type)) continue;
            
            // If predicate is subclassof, continue
            // We are only adding subclassof on node creation and to lowest superclass
            if (pred.equals(RDFS.subClassOf)) continue;
            
            // If property is a subject, need to update filters above
            if (subj.canAs(Property.class)) {
                if (propSet.contains(subj.as(Property.class))) {
                    System.out.println("Need to filter property "+subj.getLocalName()+"\nfullURI="+subj.getURI());
                }
            }
            
            
            if (!nodeMap.containsKey(subj)) {
                NodeEl subjEl = new NodeEl(subj.getURI());
                nodeMap.put(subj, subjEl);
                
                subjEl.label = getName(subj);
                
                if (classSet.contains(subj)) {
                    subjEl.schemaLevel = "Class";
                    subjEl.highestType = "Class";
                    
                    List<Resource> typeLeaveList = getTypeLeaves(subj);
                    
                    int typeLeafCounter = 0;
                    for(Resource typeLeaf: typeLeaveList) {
                        if (typeLeafCounter==0) subjEl.lowestType = getName(typeLeaf);
                        typeLeafCounter++;
                        
                        EdgeEl subjTypeEdge = new EdgeEl("Edge"+String.valueOf(edgeCount++));
                        edgeList.add(subjTypeEdge);
                        subjTypeEdge.source = subj.getURI();
                        subjTypeEdge.target = typeLeaf.getURI();
                        subjTypeEdge.kind = "type";
                        subjTypeEdge.label = RDF.type.getURI();
                    }
                    
                    List<Resource> superClsLeaveList = getSuperClassLeaves(subj);

                    for(Resource superClsLeaf: superClsLeaveList) {
                        EdgeEl subjSuperClsEdge = new EdgeEl("Edge"+String.valueOf(edgeCount++));
                        edgeList.add(subjSuperClsEdge);
                        subjSuperClsEdge.source = subj.getURI();
                        subjSuperClsEdge.target = superClsLeaf.getURI();
                        subjSuperClsEdge.kind = "subClassOf";
                        subjSuperClsEdge.label = RDFS.subClassOf.getURI();
                    }
                }
                else {
                    subjEl.schemaLevel = "Instance";
                    Resource highestType = getHighestType(subj);
                    subjEl.highestType = getName(highestType);
                    
                    List<Resource> typeLeaveList = getTypeLeaves(subj);
                    
                    int typeLeafCounter = 0;
                    for(Resource typeLeaf: typeLeaveList) {
                        if (typeLeafCounter==0) subjEl.lowestType = getName(typeLeaf);
                        typeLeafCounter++;
                        
                        EdgeEl subjTypeEdge = new EdgeEl("Edge"+String.valueOf(edgeCount++));
                        edgeList.add(subjTypeEdge);
                        subjTypeEdge.source = subj.getURI();
                        subjTypeEdge.target = typeLeaf.getURI();
                        subjTypeEdge.kind = "type";
                        subjTypeEdge.label = RDF.type.getURI();
                    }
                }
                
            }
            
            String objURI;
            
            if (obj.isLiteral()) {
                objURI = "Lit"+String.valueOf(literalCount++);
                NodeEl litEl = new NodeEl(objURI);
                litNodeList.add(litEl);
                litEl.highestType = "Literal";
                litEl.lowestType = "Literal";
                litEl.schemaLevel = "Literal";
                litEl.label = obj.as(Literal.class).getLexicalForm();
            } else {
                Resource objRes = obj.as(Resource.class);
                objURI = objRes.getURI();
                
                // If the object doesn't exist in the nodeMap yet, then it should be a subject at a later date...
            }
                
            
            EdgeEl edge = new EdgeEl("Edge"+String.valueOf(edgeCount++));
            edgeList.add(edge);
            edge.source = subj.getURI();
            edge.target = objURI;
            edge.kind = getName(pred);
            edge.label = pred.getURI();
            
        }
        
        
        
        // create document
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.newDocument();
        Element rootElement = doc.createElement("graphml");
        rootElement.setAttribute("xmlns","http://graphml.graphdrawing.org/xmlns");
        rootElement.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        rootElement.setAttribute("xsi:schemaLocation","http://graphml.graphdrawing.org/xmlns "
                + "http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd");
        
        doc.appendChild(rootElement);
        
        Element graph = doc.createElement("graph");
        graph.setAttribute("id", "G");
        graph.setAttribute("edgedefault", "directed");
        rootElement.appendChild(graph);
            



        // Build GraphML Document
        
        for (Element key: defaultEdgeKeys(doc)) graph.appendChild(key);
//        for (Map.Entry<Resource, Element> entry : predMap.entrySet()) graph.appendChild(entry.getValue());
        for (Map.Entry<Resource, NodeEl> entry : nodeMap.entrySet()) graph.appendChild(entry.getValue().xml(doc));
        for (NodeEl litNode: litNodeList) graph.appendChild(litNode.xml(doc));
        for (EdgeEl edge: edgeList) graph.appendChild(edge.xml(doc));
        
        
        TransformerFactory tFactory = TransformerFactory.newInstance();
        tFactory.setAttribute("indent-number", 4);
        Transformer transformer = tFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");

        // Write the XML to file
        transformer.transform(new DOMSource(doc), new StreamResult(new File(outPath)));
    }
    
    
    static String getName(Resource res) {
        if (res.equals(RDFS.Resource)) return "Resource";
        if (res.hasProperty(RDFS.label)) return res.getProperty(RDFS.label).getString();
        return res.getLocalName();
    }
    
    static Resource getHighestType(Resource res) {
        Set<Resource> resTypes = res.listProperties(RDF.type)
                .mapWith(stmt->stmt.getObject().as(Resource.class))
                .toSet();
        
        for(Resource type_i: resTypes) {
            if (!type_i.hasProperty(RDFS.subClassOf)) return type_i;
        }
        
        return RDFS.Resource;
    }
    
    static List<Resource> getTypeLeaves(Resource res) {
        Set<Resource> resTypeSet = res.listProperties(RDF.type)
                .mapWith(stmt->stmt.getObject().as(Resource.class))
                .toSet();
        
        Set<Resource> resSuperTypeSet = new HashSet();
        
        Resource lowestType=null;
        int maxDepth = -1;
        
        for(Resource type_i: resTypeSet) {
            Set<Statement> stmtList = type_i.listProperties(RDF.type).toSet();
            
            if (stmtList.size()>maxDepth) {
                maxDepth = stmtList.size();
                lowestType = type_i;
            }
            
            for(Statement stmt: stmtList) {
                Resource superType = stmt.getObject().as(Resource.class);
                if ((!superType.equals(type_i)) && resTypeSet.contains(superType)) {
                    resSuperTypeSet.add(superType);
                }
            }
        }
        
        resTypeSet.removeAll(resSuperTypeSet);
        
        List<Resource> out = new ArrayList();
        
        if (lowestType!=null) {
            out.add(lowestType);
            resTypeSet.remove(lowestType);
        }
        
        out.addAll(resTypeSet);
        
        return out;
    }
    
    
    static List<Resource> getSuperClassLeaves(Resource res) {
        Set<Resource> resSuperClsSet = res.listProperties(RDFS.subClassOf)
                .mapWith(stmt->stmt.getObject().as(Resource.class))
                .toSet();
        
        Set<Resource> resSuperSuperClsSet = new HashSet();
        
        for(Resource superCls_i: resSuperClsSet) {
            Set<Statement> stmtList = superCls_i.listProperties(RDFS.subClassOf).toSet();

            for(Statement stmt: stmtList) {
                Resource superSuperCls = stmt.getObject().as(Resource.class);
                if ((!superSuperCls.equals(superCls_i)) && resSuperClsSet.contains(superSuperCls)) {
                    resSuperSuperClsSet.add(superSuperCls);
                }
            }
        }
        
        resSuperClsSet.removeAll(resSuperSuperClsSet);
        
        List<Resource> out = new ArrayList();
        
        out.addAll(resSuperClsSet);
        
        return out;
    }
    
    
    static void setLabel(Document doc, Element node, RDFNode obj) {
        Element data = doc.createElement("data");
        data.setAttribute("key", "label");
        data.setTextContent(obj.toString());
        node.appendChild(data);
    }
    
    static Element createEdge(Document doc, Resource source, String target, Resource pred) {
        Element edge = doc.createElement("edge");
        edge.setAttribute("id", "edge"+String.valueOf(edgeCount));
        edgeCount++;
        edge.setAttribute("source", source.getURI());
        edge.setAttribute("target", target);

        Element edgeData = doc.createElement("data");
        edgeData.setAttribute("key", "label");
        edgeData.setTextContent(pred.getURI());
        edge.appendChild(edgeData);

        edgeData = doc.createElement("data");
        edgeData.setAttribute("key", "kind");
        edgeData.setTextContent(pred.getLocalName());
        edge.appendChild(edgeData);
        
        return edge;
    }
    
    static Element defaultNode(Document doc, Resource subj, String instOrClass) {
        Element node = doc.createElement("node");
        node.setAttribute("id", subj.getURI());
        Element data = doc.createElement("data");
        data.setAttribute("key", "SchemaLevel");
        data.setTextContent(instOrClass);
        node.appendChild(data);
        return node;
    }
    
    static Element defaultClass(Document doc, Resource subj) {
        Element out = defaultNode(doc, subj, "class");
        Element data = doc.createElement("data");
        data.setAttribute("key", "highestType");
        data.setTextContent("Class");
        out.appendChild(data);
        return out;
    }
    
    static Element createDefaultKey(Document doc, Resource prop) {
        Element key = doc.createElement("key");
        key.setAttribute("id", prop.getURI());
        key.setAttribute("for", "node");
        key.setAttribute("attr.type", "string");
        
        return key;
    }
    
    static List<Element> defaultEdgeKeys(Document doc) {
        List<Element> defEK = new ArrayList();
        
        Element key = doc.createElement("key");
        key.setAttribute("id", "kind");
        key.setAttribute("for", "edge");
        key.setAttribute("attr.name", "kind");
        key.setAttribute("attr.type", "string");
        
        defEK.add(key);
        
        key = doc.createElement("key");
        key.setAttribute("id", "label");
        key.setAttribute("for", "edge");
        key.setAttribute("attr.name", "label");
        key.setAttribute("attr.type", "string");
        
        defEK.add(key);
        
        key = doc.createElement("key");
        key.setAttribute("id", "highestType");
        key.setAttribute("for", "node");
        key.setAttribute("attr.name", "highestType");
        key.setAttribute("attr.type", "string");
        
        defEK.add(key);
        
        key = doc.createElement("key");
        key.setAttribute("id", "lowestType");
        key.setAttribute("for", "node");
        key.setAttribute("attr.name", "lowestType");
        key.setAttribute("attr.type", "string");
        
        defEK.add(key);
        
        key = doc.createElement("key");
        key.setAttribute("id", "label");
        key.setAttribute("for", "node");
        key.setAttribute("attr.name", "label");
        key.setAttribute("attr.type", "string");
        
        defEK.add(key);
        
        key = doc.createElement("key");
        key.setAttribute("id", "SchemaLevel");
        key.setAttribute("for", "node");
        key.setAttribute("attr.name", "SchemaLevel");
        key.setAttribute("attr.type", "string");
        
        defEK.add(key);
        
        key = doc.createElement("key");
        key.setAttribute("id", "subClassOf");
        key.setAttribute("for", "node");
        key.setAttribute("attr.name", "subClassOf");
        key.setAttribute("attr.type", "string");
        
        defEK.add(key);
        
        return defEK;
        
    }
    
    static HashMap<Property, String> getPredToKeyMap(Model model) {
        int count = 0;
        HashMap<Property, String> out = new HashMap();
        
        for (ResIterator stmts = model.listResourcesWithProperty(RDF.type, RDF.Property); stmts.hasNext(); ) {
            Resource r = stmts.next();
            if (r.canAs(Property.class)) {
                Property p = r.as(Property.class);
                if (!out.containsKey(p)) {
                    out.put(p, "d"+String.valueOf(count));
                    count++;
                }
            }
        }
        
        return out;
    }
    
}
