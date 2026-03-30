package com.automation.healing;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import java.util.*;
import java.util.logging.Logger;

/**
 * HealingEngine: Core orchestrator for AI-powered self-healing
 * Implements multiple healing strategies and ML-based strategy selection
 */
public class HealingEngine {
    private static final Logger logger = Logger.getLogger(HealingEngine.class.getName());
    
    private LocatorAnalyzer analyzer;
    private MLModel mlModel;
    private VectorStore vectorStore;
    private DOMSnapshotRepository snapshotRepo;
    private WebDriver driver;
    private int maxRetries = 3;
    private long healingTimeout = 5000; // ms

    public HealingEngine(WebDriver driver) {
        this.driver = driver;
        this.analyzer = new LocatorAnalyzer(driver);
        this.mlModel = new MLModel();
        this.vectorStore = new VectorStore();
        this.snapshotRepo = new DOMSnapshotRepository();
    }

    /**
     * Main healing method: Attempt to find and heal a broken locator
     */
    public WebElement healElement(By originalLocator, String elementDescription) {
        try {
            logger.info("Healing initiated for: " + elementDescription);
            
            // Analyze the DOM change
            DOMAnalysis analysis = analyzer.analyzeDOMChange(originalLocator);
            
            // Extract features for ML prediction
            Map<String, Double> features = extractFeatures(analysis);
            String recommendedStrategy = mlModel.predictStrategy(features);
            
            logger.info("ML recommended strategy: " + recommendedStrategy);
            
            // Apply healing strategies in priority order
            WebElement healed = applyHealing(recommendedStrategy, analysis);
            
            if (healed != null && isValidElement(healed)) {
                recordSuccess(analysis, recommendedStrategy, elementDescription);
                logger.info("✓ Element healed successfully using: " + recommendedStrategy);
                return healed;
            }
            
            // Fallback to other strategies
            return fallbackHealing(analysis, recommendedStrategy, elementDescription);
            
        } catch (Exception e) {
            logger.severe("Healing failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Apply specific healing strategy
     */
    private WebElement applyHealing(String strategy, DOMAnalysis analysis) {
        long startTime = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - startTime < healingTimeout) {
            switch (strategy) {
                case "PATH_RECOVERY":
                    WebElement pathResult = pathRecoveryHealing(analysis);
                    if (pathResult != null) return pathResult;
                    break;
                    
                case "ATTRIBUTE_MATCH":
                    WebElement attrResult = attributeMatchingHealing(analysis);
                    if (attrResult != null) return attrResult;
                    break;
                    
                case "FUZZY_MATCH":
                    WebElement fuzzyResult = fuzzyMatchingHealing(analysis);
                    if (fuzzyResult != null) return fuzzyResult;
                    break;
                    
                case "SEMANTIC_MATCH":
                    WebElement semanticResult = semanticVectorHealing(analysis);
                    if (semanticResult != null) return semanticResult;
                    break;
                    
                case "CV_MATCH":
                    WebElement cvResult = computerVisionMatching(analysis);
                    if (cvResult != null) return cvResult;
                    break;
            }
        }
        return null;
    }

    /**
     * Strategy 1: Path Recovery - Find similar XPath variations
     */
    private WebElement pathRecoveryHealing(DOMAnalysis analysis) {
        String originalPath = analysis.getOriginalXPath();
        List<String> variations = generateXPathVariations(originalPath);
        
        for (String xpath : variations) {
            try {
                WebElement element = driver.findElement(By.xpath(xpath));
                if (isValidElement(element) && matchesCharacteristics(element, analysis)) {
                    logger.info("Path recovery successful: " + xpath);
                    return element;
                }
            } catch (NoSuchElementException e) {
                // Continue to next variation
            }
        }
        return null;
    }

    /**
     * Strategy 2: Attribute Matching - Match by class, id, data attributes
     */
    private WebElement attributeMatchingHealing(DOMAnalysis analysis) {
        ElementCharacteristics chars = analysis.getCharacteristics();
        
        // Build XPath based on available attributes
        StringBuilder xpath = new StringBuilder("//" + chars.getTagName());
        
        if (chars.getId() != null && !chars.getId().isEmpty()) {
            xpath.append("[@id='").append(chars.getId()).append("']");
        } else if (chars.getClassName() != null && !chars.getClassName().isEmpty()) {
            xpath.append("[contains(@class, '").append(chars.getClassName()).append("')]");
        } else if (chars.getDataAttributes() != null) {
            chars.getDataAttributes().forEach((key, value) ->
                xpath.append("[contains(@").append(key).append(", '").append(value).append("')]")
            );
        } else if (chars.getTextContent() != null) {
            xpath.append("[text()='").append(chars.getTextContent()).append("']");
        }
        
        try {
            WebElement element = driver.findElement(By.xpath(xpath.toString()));
            logger.info("Attribute matching successful: " + xpath);
            return element;
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    /**
     * Strategy 3: Fuzzy Matching - Text and attribute similarity matching
     */
    private WebElement fuzzyMatchingHealing(DOMAnalysis analysis) {
        ElementCharacteristics targetChars = analysis.getCharacteristics();
        List<WebElement> candidates = driver.findElements(
            By.xpath("//" + targetChars.getTagName())
        );
        
        WebElement best = null;
        double maxSimilarity = 0.65;
        
        for (WebElement candidate : candidates) {
            double similarity = calculateCharacteristicsSimilarity(candidate, targetChars);
            
            if (similarity > maxSimilarity) {
                maxSimilarity = similarity;
                best = candidate;
            }
        }
        
        if (best != null) {
            logger.info("Fuzzy matching found element with similarity: " + maxSimilarity);
        }
        return best;
    }

    /**
     * Strategy 4: Semantic Vector Matching - ML embeddings similarity
     */
    private WebElement semanticVectorHealing(DOMAnalysis analysis) {
        try {
            double[] targetVector = vectorStore.getElementEmbedding(
                analysis.getCharacteristics()
            );
            
            String selector = "//" + analysis.getCharacteristics().getTagName();
            List<WebElement> candidates = driver.findElements(By.xpath(selector));
            
            WebElement best = null;
            double maxSimilarity = 0.75;
            
            for (WebElement candidate : candidates) {
                double[] candVector = vectorStore.getElementEmbedding(candidate);
                double similarity = cosineSimilarity(targetVector, candVector);
                
                if (similarity > maxSimilarity) {
                    maxSimilarity = similarity;
                    best = candidate;
                }
            }
            
            if (best != null) {
                logger.info("Semantic matching found element with cosine similarity: " + maxSimilarity);
            }
            return best;
            
        } catch (Exception e) {
            logger.warning("Semantic matching failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Strategy 5: Computer Vision Matching - Visual similarity
     */
    private WebElement computerVisionMatching(DOMAnalysis analysis) {
        try {
            byte[] targetScreenshot = analysis.getElementScreenshot();
            String selector = "//" + analysis.getCharacteristics().getTagName();
            List<WebElement> candidates = driver.findElements(By.xpath(selector));
            
            WebElement best = null;
            double maxSimilarity = 0.70;
            
            for (WebElement candidate : candidates) {
                byte[] candScreenshot = getElementScreenshot(candidate);
                double similarity = calculateImageSimilarity(targetScreenshot, candScreenshot);
                
                if (similarity > maxSimilarity) {
                    maxSimilarity = similarity;
                    best = candidate;
                }
            }
            
            return best;
            
        } catch (Exception e) {
            logger.warning("Computer vision matching failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Fallback healing when primary strategy fails
     */
    private WebElement fallbackHealing(DOMAnalysis analysis, String primaryStrategy, String description) {
        String[] fallbackOrder = getFallbackOrder(primaryStrategy);
        
        for (String strategy : fallbackOrder) {
            logger.info("Attempting fallback strategy: " + strategy);
            WebElement result = applyHealing(strategy, analysis);
            if (result != null) {
                recordSuccess(analysis, strategy, description);
                return result;
            }
        }
        
        logger.severe("All healing strategies failed for: " + description);
        return null;
    }

    /**
     * Extract features for ML model prediction
     */
    private Map<String, Double> extractFeatures(DOMAnalysis analysis) {
        Map<String, Double> features = new HashMap<>();
        
        features.put("domChangeDistance", analysis.getDOMChangeDistance());
        features.put("attributeSimilarity", analysis.getAttributeSimilarity());
        features.put("pathSimilarity", analysis.getPathSimilarity());
        features.put("textSimilarity", analysis.getTextSimilarity());
        features.put("classSimilarity", analysis.getClassSimilarity());
        features.put("parentPathSimilarity", analysis.getParentPathSimilarity());
        features.put("visibilitySimilarity", analysis.getVisibilitySimilarity());
        features.put("positionSimilarity", analysis.getPositionSimilarity());
        
        return features;
    }

    /**
     * Generate XPath variations for path recovery
     */
    private List<String> generateXPathVariations(String originalXPath) {
        List<String> variations = new ArrayList<>();
        
        // Original
        variations.add(originalXPath);
        
        // Remove specific indices
        variations.add(originalXPath.replaceAll("\\[\\d+\\]", ""));
        
        // Use contains instead of exact match
        variations.add(originalXPath.replaceAll("=", " contains(., "));
        
        // Get parent and use partial path
        String parentPath = originalXPath.substring(0, originalXPath.lastIndexOf('/'));
        variations.add(parentPath + "//*");
        
        // Use following-sibling
        variations.add(originalXPath.replaceAll("/([^/]+)$", "/following-sibling::$1"));
        
        return variations;
    }

    /**
     * Calculate cosine similarity between vectors
     */
    private double cosineSimilarity(double[] a, double[] b) {
        if (a.length != b.length) return 0;
        
        double dotProduct = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        
        if (normA == 0 || normB == 0) return 0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Calculate similarity between element characteristics
     */
    private double calculateCharacteristicsSimilarity(WebElement element, ElementCharacteristics target) {
        double similarity = 0;
        int factors = 0;
        
        try {
            // Class similarity
            if (target.getClassName() != null && !target.getClassName().isEmpty()) {
                String elementClass = element.getAttribute("class");
                if (elementClass != null) {
                    similarity += calculateStringSimilarity(elementClass, target.getClassName());
                }
                factors++;
            }
            
            // Text content similarity
            if (target.getTextContent() != null && !target.getTextContent().isEmpty()) {
                String elementText = element.getText();
                if (elementText != null) {
                    similarity += calculateStringSimilarity(elementText, target.getTextContent());
                }
                factors++;
            }
            
            // Placeholder/title attribute
            String placeholder = element.getAttribute("placeholder");
            if (placeholder != null && target.getPlaceholder() != null) {
                similarity += calculateStringSimilarity(placeholder, target.getPlaceholder());
                factors++;
            }
            
        } catch (Exception e) {
            logger.warning("Error calculating characteristics similarity: " + e.getMessage());
        }
        
        return factors > 0 ? similarity / factors : 0;
    }

    /**
     * Levenshtein distance based string similarity
     */
    private double calculateStringSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0;
        if (s1.equals(s2)) return 1.0;
        
        int longer = Math.max(s1.length(), s2.length());
        if (longer == 0) return 1.0;
        
        return (longer - getLevenshteinDistance(s1, s2)) / (double) longer;
    }

    /**
     * Levenshtein distance calculation
     */
    private int getLevenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        
        return dp[s1.length()][s2.length()];
    }

    /**
     * Calculate image similarity (placeholder - use SIFT/ORB in production)
     */
    private double calculateImageSimilarity(byte[] img1, byte[] img2) {
        // In production, use OpenCV SIFT/ORB for feature matching
        // This is a placeholder
        return Arrays.equals(img1, img2) ? 1.0 : 0.0;
    }

    /**
     * Get element screenshot
     */
    private byte[] getElementScreenshot(WebElement element) {
        // Implementation would use Selenium's screenshot capabilities
        return new byte[0];
    }

    /**
     * Validate element is still available
     */
    private boolean isValidElement(WebElement element) {
        try {
            return element != null && element.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if element matches target characteristics
     */
    private boolean matchesCharacteristics(WebElement element, DOMAnalysis analysis) {
        ElementCharacteristics target = analysis.getCharacteristics();
        double similarity = calculateCharacteristicsSimilarity(element, target);
        return similarity > 0.6;
    }

    /**
     * Determine fallback order based on primary strategy
     */
    private String[] getFallbackOrder(String primary) {
        Map<String, String[]> fallbackMap = new HashMap<>();
        fallbackMap.put("PATH_RECOVERY", new String[]{"ATTRIBUTE_MATCH", "FUZZY_MATCH", "SEMANTIC_MATCH", "CV_MATCH"});
        fallbackMap.put("ATTRIBUTE_MATCH", new String[]{"FUZZY_MATCH", "PATH_RECOVERY", "SEMANTIC_MATCH", "CV_MATCH"});
        fallbackMap.put("FUZZY_MATCH", new String[]{"SEMANTIC_MATCH", "ATTRIBUTE_MATCH", "CV_MATCH", "PATH_RECOVERY"});
        fallbackMap.put("SEMANTIC_MATCH", new String[]{"FUZZY_MATCH", "CV_MATCH", "ATTRIBUTE_MATCH", "PATH_RECOVERY"});
        fallbackMap.put("CV_MATCH", new String[]{"SEMANTIC_MATCH", "FUZZY_MATCH", "ATTRIBUTE_MATCH", "PATH_RECOVERY"});
        
        return fallbackMap.getOrDefault(primary, new String[]{"ATTRIBUTE_MATCH", "FUZZY_MATCH", "SEMANTIC_MATCH"});
    }

    /**
     * Record successful healing for model training
     */
    private void recordSuccess(DOMAnalysis analysis, String strategy, String description) {
        HealingRecord record = new HealingRecord()
            .setOriginalLocator(analysis.getOriginalXPath())
            .setStrategy(strategy)
            .setDOMAnalysis(analysis)
            .setTimestamp(System.currentTimeMillis())
            .setDescription(description)
            .setSuccess(true);
        
        snapshotRepo.saveHealingRecord(record);
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public void setHealingTimeout(long timeoutMs) {
        this.healingTimeout = timeoutMs;
    }
}
