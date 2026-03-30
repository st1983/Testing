package com.automation.healing;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import java.util.*;
import java.util.logging.Logger;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * LocatorAnalyzer: Analyzes DOM changes and generates element characteristics
 * for healing strategy selection
 */
public class LocatorAnalyzer {
    private static final Logger logger = Logger.getLogger(LocatorAnalyzer.class.getName());
    
    private WebDriver driver;
    private DOMSnapshotRepository repository;
    private JavascriptExecutor jsExecutor;

    public LocatorAnalyzer(WebDriver driver) {
        this.driver = driver;
        this.jsExecutor = (JavascriptExecutor) driver;
        this.repository = new DOMSnapshotRepository();
    }

    /**
     * Main analysis method: Compare current DOM with historical snapshots
     */
    public DOMAnalysis analyzeDOMChange(By originalLocator) {
        try {
            String currentDOM = getCurrentDOMState();
            String historicalDOM = repository.getLastSuccessfulDOM();
            
            // Compare DOM structures
            List<DOMDifference> differences = compareDOM(currentDOM, historicalDOM);
            
            // Extract element characteristics
            ElementCharacteristics characteristics = extractElementCharacteristics(originalLocator);
            
            // Calculate change metrics
            double domChangeDistance = calculateDOMDistance(differences);
            double attributeSimilarity = calculateAttributeSimilarity(characteristics);
            double pathSimilarity = calculatePathSimilarity(characteristics);
            double textSimilarity = calculateTextSimilarity(characteristics);
            double classSimilarity = calculateClassSimilarity(characteristics);
            
            // Generate XPath
            String xpath = generateXPath(characteristics);
            
            // Get element screenshot
            byte[] screenshot = getElementScreenshot(characteristics);
            
            DOMAnalysis analysis = new DOMAnalysis()
                .setCurrentDOM(currentDOM)
                .setHistoricalDOM(historicalDOM)
                .setDifferences(differences)
                .setCharacteristics(characteristics)
                .setOriginalXPath(xpath)
                .setDOMChangeDistance(domChangeDistance)
                .setAttributeSimilarity(attributeSimilarity)
                .setPathSimilarity(pathSimilarity)
                .setTextSimilarity(textSimilarity)
                .setClassSimilarity(classSimilarity)
                .setElementScreenshot(screenshot)
                .setTimestamp(System.currentTimeMillis());
            
            logger.info("DOM Analysis completed. Changes detected: " + differences.size());
            return analysis;
            
        } catch (Exception e) {
            logger.severe("Error during DOM analysis: " + e.getMessage());
            throw new RuntimeException("DOM analysis failed", e);
        }
    }

    /**
     * Extract current DOM state as JSON
     */
    private String getCurrentDOMState() {
        String script = "return document.documentElement.outerHTML;";
        return (String) jsExecutor.executeScript(script);
    }

    /**
     * Compare two DOM states and identify differences
     */
    private List<DOMDifference> compareDOM(String currentDOM, String historicalDOM) {
        List<DOMDifference> differences = new ArrayList<>();
        
        if (historicalDOM == null || historicalDOM.isEmpty()) {
            differences.add(new DOMDifference("First execution", "N/A", "N/A"));
            return differences;
        }
        
        // Parse DOM trees and compare
        String[] currentLines = currentDOM.split("\n");
        String[] historicalLines = historicalDOM.split("\n");
        
        // Simple diff algorithm (in production, use proper DOM tree comparison)
        Map<String, String> currentMap = parseDOM(currentDOM);
        Map<String, String> historicalMap = parseDOM(historicalDOM);
        
        // Find additions
        for (String key : currentMap.keySet()) {
            if (!historicalMap.containsKey(key)) {
                differences.add(new DOMDifference("Added", key, currentMap.get(key)));
            }
        }
        
        // Find deletions
        for (String key : historicalMap.keySet()) {
            if (!currentMap.containsKey(key)) {
                differences.add(new DOMDifference("Removed", key, historicalMap.get(key)));
            }
        }
        
        // Find modifications
        for (String key : currentMap.keySet()) {
            if (historicalMap.containsKey(key) && !currentMap.get(key).equals(historicalMap.get(key))) {
                differences.add(new DOMDifference("Modified", key, 
                    historicalMap.get(key) + " -> " + currentMap.get(key)));
            }
        }
        
        return differences;
    }

    /**
     * Parse DOM elements into a map for comparison
     */
    private Map<String, String> parseDOM(String dom) {
        Map<String, String> map = new HashMap<>();
        
        // Extract all elements with IDs and classes
        String[] lines = dom.split(">");
        for (String line : lines) {
            if (line.contains("id=") || line.contains("class=")) {
                String id = extractAttribute(line, "id");
                String className = extractAttribute(line, "class");
                String key = id.isEmpty() ? className : id;
                
                if (!key.isEmpty()) {
                    map.put(key, line.trim());
                }
            }
        }
        
        return map;
    }

    /**
     * Extract element characteristics from locator
     */
    private ElementCharacteristics extractElementCharacteristics(By locator) {
        try {
            // Find element using the original locator (may fail, that's okay)
            WebElement element = null;
            try {
                element = driver.findElement(locator);
            } catch (Exception e) {
                logger.warning("Cannot find element with original locator: " + e.getMessage());
            }
            
            ElementCharacteristics characteristics = new ElementCharacteristics();
            
            if (element != null) {
                characteristics.setTagName(element.getTagName());
                characteristics.setId(element.getAttribute("id"));
                characteristics.setClassName(element.getAttribute("class"));
                characteristics.setTextContent(element.getText());
                characteristics.setPlaceholder(element.getAttribute("placeholder"));
                characteristics.setType(element.getAttribute("type"));
                characteristics.setName(element.getAttribute("name"));
                characteristics.setVisible(element.isDisplayed());
                characteristics.setEnabled(element.isEnabled());
                
                // Get position
                characteristics.setX(element.getLocation().getX());
                characteristics.setY(element.getLocation().getY());
                characteristics.setWidth(element.getSize().getWidth());
                characteristics.setHeight(element.getSize().getHeight());
                
                // Extract data attributes
                Map<String, String> dataAttrs = new HashMap<>();
                List<String> attributes = (List<String>) jsExecutor.executeScript(
                    "return Object.keys(arguments[0].dataset);", element);
                for (String attr : attributes) {
                    dataAttrs.put("data-" + attr, element.getAttribute("data-" + attr));
                }
                characteristics.setDataAttributes(dataAttrs);
            }
            
            return characteristics;
            
        } catch (Exception e) {
            logger.warning("Error extracting characteristics: " + e.getMessage());
            return new ElementCharacteristics();
        }
    }

    /**
     * Generate XPath for the element
     */
    public String generateXPath(ElementCharacteristics characteristics) {
        StringBuilder xpath = new StringBuilder("//" + characteristics.getTagName());
        
        // Build XPath with available attributes
        if (characteristics.getId() != null && !characteristics.getId().isEmpty()) {
            xpath.append("[@id='").append(characteristics.getId()).append("']");
        } else {
            List<String> predicates = new ArrayList<>();
            
            if (characteristics.getClassName() != null && !characteristics.getClassName().isEmpty()) {
                predicates.add("contains(@class, '" + characteristics.getClassName() + "')");
            }
            if (characteristics.getType() != null && !characteristics.getType().isEmpty()) {
                predicates.add("@type='" + characteristics.getType() + "'");
            }
            if (characteristics.getName() != null && !characteristics.getName().isEmpty()) {
                predicates.add("@name='" + characteristics.getName() + "'");
            }
            if (characteristics.getTextContent() != null && !characteristics.getTextContent().isEmpty()) {
                predicates.add("text()='" + characteristics.getTextContent() + "'");
            }
            
            if (!predicates.isEmpty()) {
                xpath.append("[");
                xpath.append(String.join(" and ", predicates));
                xpath.append("]");
            }
        }
        
        return xpath.toString();
    }

    /**
     * Generate CSS selector for the element
     */
    public String generateCSSSelector(ElementCharacteristics characteristics) {
        StringBuilder selector = new StringBuilder(characteristics.getTagName());
        
        if (characteristics.getId() != null && !characteristics.getId().isEmpty()) {
            selector.append("#").append(characteristics.getId());
        } else if (characteristics.getClassName() != null && !characteristics.getClassName().isEmpty()) {
            String[] classes = characteristics.getClassName().split(" ");
            for (String cls : classes) {
                selector.append(".").append(cls);
            }
        }
        
        return selector.toString();
    }

    /**
     * Calculate DOM change distance using diff metrics
     */
    private double calculateDOMDistance(List<DOMDifference> differences) {
        if (differences.isEmpty()) return 0.0;
        return Math.min(1.0, (double) differences.size() / 100);
    }

    /**
     * Calculate attribute similarity score
     */
    private double calculateAttributeSimilarity(ElementCharacteristics characteristics) {
        double score = 0;
        int count = 0;
        
        if (characteristics.getId() != null && !characteristics.getId().isEmpty()) {
            score += 1.0;
            count++;
        }
        if (characteristics.getClassName() != null && !characteristics.getClassName().isEmpty()) {
            score += 0.8;
            count++;
        }
        if (characteristics.getName() != null && !characteristics.getName().isEmpty()) {
            score += 0.7;
            count++;
        }
        
        return count > 0 ? score / count : 0;
    }

    /**
     * Calculate XPath similarity using string comparison
     */
    private double calculatePathSimilarity(ElementCharacteristics characteristics) {
        String xpath = generateXPath(characteristics);
        if (xpath.isEmpty()) return 0;
        
        // Simpler paths are more stable (similarity based on length)
        int pathDepth = xpath.split("/").length;
        return Math.max(0, 1.0 - (pathDepth / 20.0));
    }

    /**
     * Calculate text content similarity
     */
    private double calculateTextSimilarity(ElementCharacteristics characteristics) {
        if (characteristics.getTextContent() == null || characteristics.getTextContent().isEmpty()) {
            return 0;
        }
        
        String text = characteristics.getTextContent();
        // Text-based locators are less stable if text changes frequently
        return text.length() > 0 ? 0.6 : 0;
    }

    /**
     * Calculate class name similarity
     */
    private double calculateClassSimilarity(ElementCharacteristics characteristics) {
        if (characteristics.getClassName() == null || characteristics.getClassName().isEmpty()) {
            return 0;
        }
        
        String[] classes = characteristics.getClassName().split(" ");
        // More stable with multiple consistent classes
        return Math.min(1.0, classes.length * 0.3);
    }

    /**
     * Get element screenshot for CV matching
     */
    private byte[] getElementScreenshot(ElementCharacteristics characteristics) {
        try {
            // In production: Use Selenium's TakesScreenshot or element screenshot capability
            // This is a placeholder
            String script = "return arguments[0].getBoundingClientRect();";
            // Could implement actual screenshot capture here
            return new byte[0];
        } catch (Exception e) {
            logger.warning("Cannot capture element screenshot: " + e.getMessage());
            return new byte[0];
        }
    }

    /**
     * Extract attribute value from HTML string
     */
    private String extractAttribute(String html, String attributeName) {
        String pattern = attributeName + "=\"([^\"]*)\"";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(html);
        return m.find() ? m.group(1) : "";
    }

    /**
     * Get parent XPath of an element
     */
    public String getParentXPath(String xpath) {
        int lastSlash = xpath.lastIndexOf('/');
        return lastSlash > 0 ? xpath.substring(0, lastSlash) : "/";
    }

    /**
     * Find similar elements based on characteristics
     */
    public List<WebElement> findSimilarElements(ElementCharacteristics characteristics, double threshold) {
        List<WebElement> similar = new ArrayList<>();
        String selector = "//" + characteristics.getTagName();
        
        try {
            List<WebElement> candidates = driver.findElements(By.xpath(selector));
            
            for (WebElement candidate : candidates) {
                ElementCharacteristics candChars = new ElementCharacteristics();
                candChars.setTagName(candidate.getTagName());
                candChars.setClassName(candidate.getAttribute("class"));
                candChars.setId(candidate.getAttribute("id"));
                candChars.setTextContent(candidate.getText());
                
                // This would be expanded with full comparison
                if (compareCharacteristics(characteristics, candChars) > threshold) {
                    similar.add(candidate);
                }
            }
        } catch (Exception e) {
            logger.warning("Error finding similar elements: " + e.getMessage());
        }
        
        return similar;
    }

    /**
     * Compare two element characteristics
     */
    private double compareCharacteristics(ElementCharacteristics c1, ElementCharacteristics c2) {
        double similarity = 0;
        
        if (c1.getClassName() != null && c1.getClassName().equals(c2.getClassName())) {
            similarity += 0.4;
        }
        if (c1.getId() != null && c1.getId().equals(c2.getId())) {
            similarity += 0.4;
        }
        if (c1.getTextContent() != null && c1.getTextContent().equals(c2.getTextContent())) {
            similarity += 0.2;
        }
        
        return similarity;
    }

    // Inner classes
    public static class DOMDifference {
        private String type;
        private String element;
        private String change;
        
        public DOMDifference(String type, String element, String change) {
            this.type = type;
            this.element = element;
            this.change = change;
        }
        
        public String getType() { return type; }
        public String getElement() { return element; }
        public String getChange() { return change; }
    }
}
