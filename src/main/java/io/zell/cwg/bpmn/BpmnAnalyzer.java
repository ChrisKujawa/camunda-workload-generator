package io.zell.cwg.bpmn;

import io.zell.cwg.bpmn.BpmnAnalysis.CallActivity;
import io.zell.cwg.bpmn.BpmnAnalysis.DmnReference;
import io.zell.cwg.bpmn.BpmnAnalysis.MessageReference;
import io.zell.cwg.bpmn.BpmnAnalysis.StaticJobType;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public final class BpmnAnalyzer {

  public BpmnAnalysis analyze(final Path bpmnFile) throws IOException {
    final var document = parse(bpmnFile);
    return new BpmnAnalysis(
        bpmnFile,
        processIds(document),
        staticJobTypes(document),
        callActivities(document),
        messageReferences(document),
        dmnReferences(document));
  }

  private Document parse(final Path bpmnFile) throws IOException {
    try {
      final var factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      return factory.newDocumentBuilder().parse(bpmnFile.toFile());
    } catch (final ParserConfigurationException | SAXException e) {
      throw new IOException(
          "Could not parse BPMN file %s: %s".formatted(bpmnFile, e.getMessage()), e);
    }
  }

  private List<String> processIds(final Document document) {
    final var processIds = new ArrayList<String>();
    forEachElement(
        document, "process", process -> addIfPresent(processIds, process.getAttribute("id")));
    return processIds;
  }

  private List<StaticJobType> staticJobTypes(final Document document) {
    final var jobTypes = new ArrayList<StaticJobType>();
    forEachElement(
        document,
        "taskDefinition",
        taskDefinition -> {
          final var type = taskDefinition.getAttribute("type");
          if (isStaticValue(type)) {
            final var owner = ownerElement(taskDefinition);
            jobTypes.add(
                new StaticJobType(
                    owner == null ? "" : owner.getAttribute("id"),
                    owner == null ? "" : owner.getAttribute("name"),
                    type));
          }
        });
    return jobTypes;
  }

  private List<CallActivity> callActivities(final Document document) {
    final var callActivities = new ArrayList<CallActivity>();
    forEachElement(
        document,
        "callActivity",
        callActivity -> {
          final var calledProcessId =
              firstNonBlank(
                  callActivity.getAttribute("calledElement"),
                  childAttribute(callActivity, "calledElement", "processId"));
          callActivities.add(
              new CallActivity(
                  callActivity.getAttribute("id"),
                  callActivity.getAttribute("name"),
                  calledProcessId));
        });
    return callActivities;
  }

  private List<MessageReference> messageReferences(final Document document) {
    final var messageReferences = new ArrayList<MessageReference>();
    forEachElement(
        document,
        "messageEventDefinition",
        messageEventDefinition -> {
          final var messageRef = messageEventDefinition.getAttribute("messageRef");
          if (!messageRef.isBlank()) {
            final var owner = ownerElement(messageEventDefinition);
            messageReferences.add(
                new MessageReference(owner == null ? "" : owner.getAttribute("id"), messageRef));
          }
        });
    return messageReferences;
  }

  private List<DmnReference> dmnReferences(final Document document) {
    final var dmnReferences = new ArrayList<DmnReference>();
    forEachElement(
        document,
        "calledDecision",
        calledDecision -> {
          final var decisionId = calledDecision.getAttribute("decisionId");
          if (!decisionId.isBlank()) {
            final var owner = ownerElement(calledDecision);
            dmnReferences.add(
                new DmnReference(owner == null ? "" : owner.getAttribute("id"), decisionId));
          }
        });
    return dmnReferences;
  }

  private static void forEachElement(
      final Document document, final String expectedLocalName, final ElementConsumer consumer) {
    final var elements = document.getElementsByTagName("*");
    for (var i = 0; i < elements.getLength(); i++) {
      final var element = (Element) elements.item(i);
      if (expectedLocalName.equals(localName(element))) {
        consumer.accept(element);
      }
    }
  }

  private static Element ownerElement(final Element element) {
    var current = element.getParentNode();
    while (current != null && current.getNodeType() == Node.ELEMENT_NODE) {
      final var currentElement = (Element) current;
      final var name = localName(currentElement);
      if (!"extensionElements".equals(name) && !"definitions".equals(name)) {
        return currentElement;
      }
      current = current.getParentNode();
    }
    return null;
  }

  private static String childAttribute(
      final Element element, final String expectedLocalName, final String attributeName) {
    final var children = element.getElementsByTagName("*");
    for (var i = 0; i < children.getLength(); i++) {
      final var child = (Element) children.item(i);
      if (expectedLocalName.equals(localName(child))) {
        final var value = child.getAttribute(attributeName);
        if (!value.isBlank()) {
          return value;
        }
      }
    }
    return null;
  }

  private static void addIfPresent(final List<String> values, final String candidate) {
    if (candidate != null && !candidate.isBlank()) {
      values.add(candidate);
    }
  }

  private static boolean isStaticValue(final String value) {
    return value != null && !value.isBlank() && !value.stripLeading().startsWith("=");
  }

  private static String firstNonBlank(final String first, final String second) {
    if (first != null && !first.isBlank()) {
      return first;
    }
    return second == null || second.isBlank() ? null : second;
  }

  private static String localName(final Element element) {
    final var localName = element.getLocalName();
    if (localName != null) {
      return localName;
    }
    final var nodeName = element.getNodeName();
    final var prefixEnd = nodeName.indexOf(':');
    return prefixEnd >= 0 ? nodeName.substring(prefixEnd + 1) : nodeName;
  }

  @FunctionalInterface
  private interface ElementConsumer {
    void accept(Element element);
  }
}
