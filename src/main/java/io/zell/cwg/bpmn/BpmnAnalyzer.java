package io.zell.cwg.bpmn;

import io.zell.cwg.bpmn.BpmnAnalysis.CallActivity;
import io.zell.cwg.bpmn.BpmnAnalysis.DmnReference;
import io.zell.cwg.bpmn.BpmnAnalysis.HappyPathNode;
import io.zell.cwg.bpmn.BpmnAnalysis.MessageReference;
import io.zell.cwg.bpmn.BpmnAnalysis.ProcessPath;
import io.zell.cwg.bpmn.BpmnAnalysis.StaticJobType;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public final class BpmnAnalyzer {

  private static final Set<String> FLOW_NODE_TYPES =
      Set.of(
          "startEvent",
          "endEvent",
          "serviceTask",
          "sendTask",
          "receiveTask",
          "userTask",
          "businessRuleTask",
          "callActivity",
          "exclusiveGateway",
          "inclusiveGateway",
          "parallelGateway",
          "eventBasedGateway",
          "subProcess",
          "task",
          "scriptTask",
          "manualTask",
          "intermediateCatchEvent",
          "intermediateThrowEvent");

  public BpmnAnalysis analyze(final Path bpmnFile) throws IOException {
    final var document = parse(bpmnFile);
    return new BpmnAnalysis(
        bpmnFile,
        processIds(document),
        processPaths(document),
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

  private List<ProcessPath> processPaths(final Document document) {
    final var processPaths = new ArrayList<ProcessPath>();
    forEachElement(
        document,
        "process",
        process -> {
          if (!process.getAttribute("id").isBlank()) {
            processPaths.add(processPath(process));
          }
        });
    return processPaths;
  }

  private ProcessPath processPath(final Element process) {
    final var processId = process.getAttribute("id");
    final var flowNodesById = new LinkedHashMap<String, Element>();
    final var sequenceFlowTargets = new LinkedHashMap<String, String>();
    final var startEvents = new ArrayList<Element>();
    final var jobTypesByElementId = staticJobTypesByElementId(process);

    for (final var child : directChildElements(process)) {
      final var id = child.getAttribute("id");
      if (id.isBlank()) {
        continue;
      }

      final var localName = localName(child);
      if ("sequenceFlow".equals(localName)) {
        sequenceFlowTargets.put(id, child.getAttribute("targetRef"));
      } else if (FLOW_NODE_TYPES.contains(localName)) {
        flowNodesById.put(id, child);
        if ("startEvent".equals(localName)) {
          startEvents.add(child);
        }
      }
    }

    if (startEvents.isEmpty()) {
      return new ProcessPath(processId, List.of(), false);
    }

    final var happyPath = new ArrayList<HappyPathNode>();
    final var visited = new java.util.HashSet<String>();
    var current = startEvents.get(0);
    var completePath = false;

    while (current != null && visited.add(current.getAttribute("id"))) {
      happyPath.add(happyPathNode(current, jobTypesByElementId));
      if ("endEvent".equals(localName(current))) {
        completePath = true;
        break;
      }

      final var nextFlowId = nextFlowId(current);
      if (nextFlowId == null) {
        break;
      }
      final var targetId = sequenceFlowTargets.get(nextFlowId);
      current = targetId == null ? null : flowNodesById.get(targetId);
    }

    return new ProcessPath(processId, happyPath, completePath);
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
                    type,
                    owner == null ? "" : processId(owner)));
          }
        });
    return jobTypes;
  }

  private Map<String, String> staticJobTypesByElementId(final Element process) {
    final var jobTypes = new LinkedHashMap<String, String>();
    final var elements = process.getElementsByTagName("*");
    for (var i = 0; i < elements.getLength(); i++) {
      final var element = (Element) elements.item(i);
      if ("taskDefinition".equals(localName(element))) {
        final var type = element.getAttribute("type");
        if (isStaticValue(type)) {
          final var owner = ownerElement(element);
          if (owner != null) {
            jobTypes.put(owner.getAttribute("id"), type);
          }
        }
      }
    }
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
                  calledProcessId,
                  processId(callActivity)));
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
                new MessageReference(
                    owner == null ? "" : owner.getAttribute("id"),
                    messageRef,
                    owner == null ? "" : processId(owner)));
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
                new DmnReference(
                    owner == null ? "" : owner.getAttribute("id"),
                    decisionId,
                    owner == null ? "" : processId(owner)));
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

  private static String processId(final Element element) {
    var current = element;
    while (current != null) {
      if ("process".equals(localName(current))) {
        return current.getAttribute("id");
      }
      final var parent = current.getParentNode();
      current =
          parent != null && parent.getNodeType() == Node.ELEMENT_NODE ? (Element) parent : null;
    }
    return "";
  }

  private static List<Element> directChildElements(final Element element) {
    final var children = new ArrayList<Element>();
    var child = element.getFirstChild();
    while (child != null) {
      if (child.getNodeType() == Node.ELEMENT_NODE) {
        children.add((Element) child);
      }
      child = child.getNextSibling();
    }
    return children;
  }

  private static HappyPathNode happyPathNode(
      final Element element, final Map<String, String> jobTypesByElementId) {
    final var elementId = element.getAttribute("id");
    return new HappyPathNode(
        elementId,
        element.getAttribute("name"),
        localName(element),
        jobTypesByElementId.get(elementId));
  }

  private static String nextFlowId(final Element element) {
    final var outgoing = new ArrayList<String>();
    for (final var child : directChildElements(element)) {
      if ("outgoing".equals(localName(child))) {
        final var flowId = child.getTextContent().strip();
        if (!flowId.isBlank()) {
          outgoing.add(flowId);
        }
      }
    }
    if (outgoing.isEmpty()) {
      return null;
    }
    final var defaultFlow = element.getAttribute("default");
    if (!defaultFlow.isBlank() && outgoing.contains(defaultFlow)) {
      return defaultFlow;
    }
    return outgoing.get(0);
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
