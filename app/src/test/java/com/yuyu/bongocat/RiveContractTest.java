package com.yuyu.bongocat;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.*;
import static org.junit.Assert.*;

public class RiveContractTest {
    @Test public void layeredBodyAndSkinnedTailKeepAppearanceBindings() throws Exception {
        Document doc = model();
        for (String name : new String[]{"Body", "Head Fill", "Tail Silhouette", "Fore Left Shape",
                "Fore Right Shape", "Hind Left Shape", "Hind Right Shape", "Frame Head Fur"}) {
            Element shape = named(doc, "Shape", name);
            Element fill = (Element) shape.getElementsByTagName("Fill").item(0);
            assertNotNull(name, fill);
            Element binding = (Element) fill.getElementsByTagName("DataBindContext").item(0);
            assertNotNull(name, binding);
            assertEquals("0:126-0:127", binding.getAttribute("sourcePathIds"));
        }
        NodeList shapes = doc.getElementsByTagName("Shape");
        for (int i = 0; i < shapes.getLength(); i++) {
            Element shape = (Element) shapes.item(i);
            String name = shape.getAttribute("name");
            if (!name.startsWith("Frame ") || !name.toLowerCase().endsWith("fur")) continue;
            Element fill = (Element) shape.getElementsByTagName("Fill").item(0);
            Element binding = (Element) fill.getElementsByTagName("DataBindContext").item(0);
            assertNotNull("逐帧轮廓必须随毛色变化：" + name, binding);
            assertEquals("0:126-0:127", binding.getAttribute("sourcePathIds"));
        }
        Element tail = named(doc, "Shape", "Tail Silhouette");
        assertEquals("true", ((Element) tail.getElementsByTagName("PointsPath").item(0)).getAttribute("isClosed"));
        assertEquals(1, tail.getElementsByTagName("Skin").getLength());
        assertEquals(3, tail.getElementsByTagName("Tendon").getLength());
        assertEquals(tail.getElementsByTagName("CubicDetachedVertex").getLength(),
                tail.getElementsByTagName("CubicWeight").getLength());
        NodeList weights = tail.getElementsByTagName("CubicWeight");
        for (int i = 0; i < weights.getLength(); i++) {
            Element weight = (Element) weights.item(i);
            for (String key : new String[]{"values", "inValues", "outValues"}) {
                int packed = Integer.parseInt(weight.getAttribute(key));
                assertEquals(255, (packed & 255) + ((packed >> 8) & 255));
            }
        }
        for (String name : new String[]{"Bow", "Hat", "Crown", "Flower", "Scarf"}) {
            boolean found = false;
            NodeList groups = doc.getElementsByTagName("Node");
            for (int i = 0; i < groups.getLength(); i++) {
                if (name.equals(((Element) groups.item(i)).getAttribute("name"))) found = true;
            }
            assertTrue("Missing Rive costume: " + name, found);
        }
        assertEquals(1, doc.getElementsByTagName("StateMachine").getLength());
        assertEquals(3, doc.getElementsByTagName("StateMachineLayer").getLength());
        assertEquals(43, doc.getElementsByTagName("LinearAnimation").getLength());
        named(doc, "ViewModelPropertyBoolean", "holdSign");
        named(doc, "Node", "Sign Board");
        assertEquals(3, doc.getElementsByTagName("RootBone").getLength());
        assertEquals(6, doc.getElementsByTagName("Bone").getLength());
        for (String action : new String[]{"Blink", "Lick", "Tail", "Stretch", "Yawn", "Look", "Crawl", "Roll", "LieCrawl"}) {
            named(doc, "ViewModelPropertyTrigger", "play" + action);
        }
        assertEquals(0, doc.getElementsByTagName("Image").getLength());
    }

    @Test public void frameAnimationSelectsExistingDrawingsAndResetsAfterRoll() throws Exception {
        Document doc = model();
        Element solo = named(doc, "Solo", "Frame Art");
        String empty = named(doc, "Node", "Frame Empty").getAttribute("id");
        Set<String> children = new HashSet<>();
        NodeList groups = solo.getChildNodes();
        for (int i = 0; i < groups.getLength(); i++) {
            if (groups.item(i) instanceof Element) children.add(((Element) groups.item(i)).getAttribute("id"));
        }
        NodeList animations = doc.getElementsByTagName("LinearAnimation");
        for (int i = 0; i < animations.getLength(); i++) {
            Element animation = (Element) animations.item(i);
            String name = animation.getAttribute("name");
            if (name.equals("Inactivity") || name.startsWith("Costume")) continue;
            NodeList keys = track(animation, solo.getAttribute("id"), "activeComponentId").getElementsByTagName("KeyFrameId");
            assertTrue(name, keys.getLength() > 0);
            for (int j = 0; j < keys.getLength(); j++) {
                Element key = (Element) keys.item(j);
                assertTrue(name + " 引用了不存在的姿势", children.contains(key.getAttribute("value")));
                assertEquals("hold", key.getAttribute("interpolationType"));
            }
            if (name.equals("Roll")) {
                assertEquals(empty, ((Element) keys.item(0)).getAttribute("value"));
                assertEquals(empty, ((Element) keys.item(keys.getLength() - 1)).getAttribute("value"));
            }
            if (name.equals("Lick") || name.equals("Yawn") || name.equals("Idle")) {
                for (int j = 0; j < keys.getLength(); j++) assertEquals(empty, ((Element) keys.item(j)).getAttribute("value"));
            }
        }
    }

    @Test public void lickingBendsAttachedArmsInsteadOfTranslatingPaws() throws Exception {
        Document doc = model();
        Element lick = named(doc, "LinearAnimation", "Lick");
        for (String side : new String[]{"Left", "Right"}) {
            Element arm = named(doc, "Shape", "Fore " + side + " Shape");
            assertEquals(1, arm.getElementsByTagName("Skin").getLength());
            assertEquals(3, arm.getElementsByTagName("Tendon").getLength());
            NodeList vertices = arm.getElementsByTagName("CubicDetachedVertex");
            for (int i : new int[]{0, vertices.getLength() - 1}) {
                Element weight = (Element) ((Element) vertices.item(i)).getElementsByTagName("CubicWeight").item(0);
                assertEquals("1", weight.getAttribute("indices"));
                assertEquals("255", weight.getAttribute("values"));
            }
        }
        Element group = named(doc, "Node", "Fore Left");
        for (String property : new String[]{"x", "y", "rotation"}) {
            assertTrackConstant(lick, group.getAttribute("id"), property, 0);
        }
        Element shoulder = named(doc, "RootBone", "Fore Left Shoulder");
        assertTrackConstant(lick, shoulder.getAttribute("id"), "x", Double.parseDouble(shoulder.getAttribute("x")));
        assertTrackConstant(lick, shoulder.getAttribute("id"), "y", Double.parseDouble(shoulder.getAttribute("y")));
        for (String joint : new String[]{"Shoulder", "Elbow", "Wrist"}) {
            Element bone = named(doc, joint.equals("Shoulder") ? "RootBone" : "Bone", "Fore Left " + joint);
            Element track = track(lick, bone.getAttribute("id"), "rotation");
            NodeList keys = track.getElementsByTagName("KeyFrameDouble");
            double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < keys.getLength(); i++) {
                double value = Double.parseDouble(((Element) keys.item(i)).getAttribute("value"));
                min = Math.min(min, value); max = Math.max(max, value);
            }
            assertTrue(joint + " should articulate", max - min > .05);
        }
    }

    private Document model() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        File root = new File(System.getProperty("bongocat.projectDir", "."));
        return factory.newDocumentBuilder().parse(new File(root, "rive/scene.rml"));
    }

    private void assertTrackConstant(Element animation, String id, String property, double expected) {
        NodeList keys = track(animation, id, property).getElementsByTagName("KeyFrameDouble");
        assertTrue(keys.getLength() > 0);
        for (int i = 0; i < keys.getLength(); i++) {
            assertEquals(expected, Double.parseDouble(((Element) keys.item(i)).getAttribute("value")), .00001);
        }
    }

    private Element track(Element animation, String id, String property) {
        NodeList objects = animation.getElementsByTagName("KeyedObject");
        for (int i = 0; i < objects.getLength(); i++) {
            Element object = (Element) objects.item(i);
            if (!id.equals(object.getAttribute("objectId"))) continue;
            NodeList properties = object.getElementsByTagName("KeyedProperty");
            for (int j = 0; j < properties.getLength(); j++) {
                Element entry = (Element) properties.item(j);
                if (property.equals(entry.getAttribute("property"))) return entry;
            }
        }
        throw new AssertionError("Missing track: " + id + "/" + property);
    }

    private Element named(Document doc, String type, String name) {
        NodeList list = doc.getElementsByTagName(type);
        for (int i = 0; i < list.getLength(); i++) {
            Element element = (Element) list.item(i);
            if (name.equals(element.getAttribute("name"))) return element;
        }
        throw new AssertionError("Missing " + type + ": " + name);
    }
}
