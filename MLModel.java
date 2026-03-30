package com.automation.healing.ml;

import java.util.*;
import java.util.logging.Logger;
import java.io.*;

/**
 * MLModel: Random Forest based strategy predictor
 * Predicts optimal healing strategy based on DOM analysis features
 */
public class MLModel {
    private static final Logger logger = Logger.getLogger(MLModel.class.getName());
    
    private RandomForestClassifier classifier;
    private FeatureExtractor featureExtractor;
    private String modelPath = "mlmodels/healer_model.pkl";
    
    // Strategy mapping
    private static final String[] STRATEGIES = {
        "PATH_RECOVERY",
        "ATTRIBUTE_MATCH",
        "FUZZY_MATCH",
        "SEMANTIC_MATCH",
        "CV_MATCH"
    };
    
    public MLModel() {
        this.featureExtractor = new FeatureExtractor();
        loadModel();
    }

    /**
     * Load pre-trained model from disk
     */
    private void loadModel() {
        try {
            File modelFile = new File(modelPath);
            if (modelFile.exists()) {
                this.classifier = new RandomForestClassifier();
                this.classifier.load(modelPath);
                logger.info("ML Model loaded successfully from: " + modelPath);
            } else {
                logger.warning("Model file not found. Using default random strategy selection.");
                initializeDefaultModel();
            }
        } catch (Exception e) {
            logger.warning("Failed to load model: " + e.getMessage() + ". Using default.");
            initializeDefaultModel();
        }
    }

    /**
     * Initialize default model (fallback)
     */
    private void initializeDefaultModel() {
        this.classifier = new RandomForestClassifier();
    }

    /**
     * Predict best healing strategy based on features
     */
    public String predictStrategy(Map<String, Double> features) {
        try {
            if (classifier == null) {
                return getDefaultStrategy(features);
            }
            
            // Convert features to array
            double[] featureArray = featureExtractor.extractFeatureVector(features);
            
            // Get prediction from model
            int prediction = classifier.predict(featureArray);
            
            // Get confidence scores
            double[] probabilities = classifier.predictProba(featureArray);
            double confidence = probabilities[prediction];
            
            String strategy = STRATEGIES[prediction];
            logger.info("Strategy predicted: " + strategy + " (confidence: " + 
                String.format("%.2f", confidence * 100) + "%)");
            
            return strategy;
            
        } catch (Exception e) {
            logger.warning("Prediction error: " + e.getMessage() + ". Using default.");
            return getDefaultStrategy(features);
        }
    }

    /**
     * Get prediction probabilities for all strategies
     */
    public Map<String, Double> predictStrategiesProbabilities(Map<String, Double> features) {
        try {
            double[] featureArray = featureExtractor.extractFeatureVector(features);
            double[] probabilities = classifier.predictProba(featureArray);
            
            Map<String, Double> result = new LinkedHashMap<>();
            for (int i = 0; i < STRATEGIES.length; i++) {
                result.put(STRATEGIES[i], probabilities[i]);
            }
            
            return result;
            
        } catch (Exception e) {
            logger.warning("Error getting probabilities: " + e.getMessage());
            return getDefaultProbabilities();
        }
    }

    /**
     * Get confidence score for prediction
     */
    public double getPredictionConfidence(Map<String, Double> features) {
        try {
            double[] featureArray = featureExtractor.extractFeatureVector(features);
            double[] probabilities = classifier.predictProba(featureArray);
            return Arrays.stream(probabilities).max().orElse(0.0);
        } catch (Exception e) {
            return 0.5; // Default confidence
        }
    }

    /**
     * Determine strategy using default heuristics
     */
    private String getDefaultStrategy(Map<String, Double> features) {
        double pathSim = features.getOrDefault("pathSimilarity", 0.0);
        double attrSim = features.getOrDefault("attributeSimilarity", 0.0);
        double textSim = features.getOrDefault("textSimilarity", 0.0);
        double classSim = features.getOrDefault("classSimilarity", 0.0);
        double changeDistance = features.getOrDefault("domChangeDistance", 0.5);
        
        // Heuristic-based strategy selection
        if (pathSim > 0.7) {
            return "PATH_RECOVERY";
        } else if (attrSim > 0.6) {
            return "ATTRIBUTE_MATCH";
        } else if ((textSim + classSim) / 2 > 0.6) {
            return "FUZZY_MATCH";
        } else if (changeDistance < 0.5) {
            return "SEMANTIC_MATCH";
        } else {
            return "CV_MATCH";
        }
    }

    /**
     * Get default probability distribution
     */
    private Map<String, Double> getDefaultProbabilities() {
        Map<String, Double> probs = new LinkedHashMap<>();
        double weight = 1.0 / STRATEGIES.length;
        for (String strategy : STRATEGIES) {
            probs.put(strategy, weight);
        }
        return probs;
    }

    /**
     * Train model on historical data
     */
    public void trainModel(List<TrainingRecord> trainingData) {
        try {
            if (trainingData.isEmpty()) {
                logger.warning("No training data provided");
                return;
            }
            
            logger.info("Starting model training with " + trainingData.size() + " records");
            
            // Extract features and labels
            List<double[]> features = new ArrayList<>();
            List<Integer> labels = new ArrayList<>();
            
            for (TrainingRecord record : trainingData) {
                double[] featureVector = featureExtractor.extractFeatureVector(
                    record.getDOMFeatures()
                );
                features.add(featureVector);
                labels.add(strategyToIndex(record.getSuccessfulStrategy()));
            }
            
            // Convert to arrays
            double[][] X = features.toArray(new double[0][]);
            int[] y = labels.stream().mapToInt(Integer::intValue).toArray();
            
            // Train classifier
            this.classifier = new RandomForestClassifier();
            classifier.train(X, y, 100); // 100 trees
            
            // Save model
            classifier.save(modelPath);
            logger.info("Model training completed and saved to: " + modelPath);
            
            // Calculate training metrics
            calculateTrainingMetrics(X, y);
            
        } catch (Exception e) {
            logger.severe("Model training failed: " + e.getMessage());
            throw new RuntimeException("Training error", e);
        }
    }

    /**
     * Calculate and log training metrics
     */
    private void calculateTrainingMetrics(double[][] X, int[] y) {
        try {
            int correct = 0;
            
            for (int i = 0; i < X.length; i++) {
                int prediction = classifier.predict(X[i]);
                if (prediction == y[i]) {
                    correct++;
                }
            }
            
            double accuracy = (double) correct / y.length;
            logger.info("Training Accuracy: " + String.format("%.2f%%", accuracy * 100));
            
            // Per-class accuracy
            Map<Integer, Integer> classCorrect = new HashMap<>();
            Map<Integer, Integer> classTotal = new HashMap<>();
            
            for (int i = 0; i < y.length; i++) {
                int label = y[i];
                classTotal.put(label, classTotal.getOrDefault(label, 0) + 1);
                
                if (classifier.predict(X[i]) == label) {
                    classCorrect.put(label, classCorrect.getOrDefault(label, 0) + 1);
                }
            }
            
            for (int strategy : classCorrect.keySet()) {
                int stratCorrect = classCorrect.get(strategy);
                int stratTotal = classTotal.get(strategy);
                double stratAccuracy = (double) stratCorrect / stratTotal;
                logger.info("  " + STRATEGIES[strategy] + ": " + 
                    String.format("%.2f%%", stratAccuracy * 100));
            }
            
        } catch (Exception e) {
            logger.warning("Error calculating metrics: " + e.getMessage());
        }
    }

    /**
     * Convert strategy name to index
     */
    private int strategyToIndex(String strategy) {
        for (int i = 0; i < STRATEGIES.length; i++) {
            if (STRATEGIES[i].equals(strategy)) {
                return i;
            }
        }
        return 0; // Default to PATH_RECOVERY
    }

    /**
     * Get feature importance scores
     */
    public Map<String, Double> getFeatureImportance() {
        try {
            return classifier.getFeatureImportance(featureExtractor.getFeatureNames());
        } catch (Exception e) {
            logger.warning("Error getting feature importance: " + e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Update model with new successful healing record
     */
    public void updateWithNewRecord(TrainingRecord record) {
        try {
            // Could implement online learning here
            // For now, mark for next batch training
            logger.info("New healing record recorded for retraining");
        } catch (Exception e) {
            logger.warning("Error updating model: " + e.getMessage());
        }
    }

    /**
     * Retrain model periodically (should be called in a scheduled task)
     */
    public void retrainIfNeeded(List<TrainingRecord> recentData) {
        if (recentData.size() > 100) { // Retrain every 100 new records
            logger.info("Triggering model retraining with " + recentData.size() + " new records");
            trainModel(recentData);
        }
    }

    public static class TrainingRecord {
        private Map<String, Double> domFeatures;
        private String successfulStrategy;
        private long timestamp;
        private boolean success;
        
        public TrainingRecord(Map<String, Double> domFeatures, String successfulStrategy) {
            this.domFeatures = domFeatures;
            this.successfulStrategy = successfulStrategy;
            this.timestamp = System.currentTimeMillis();
            this.success = true;
        }
        
        public Map<String, Double> getDOMFeatures() { return domFeatures; }
        public String getSuccessfulStrategy() { return successfulStrategy; }
        public long getTimestamp() { return timestamp; }
        public boolean isSuccess() { return success; }
        
        public void setSuccess(boolean success) { this.success = success; }
    }
}

/**
 * Feature Extractor: Converts DOM analysis features to ML feature vector
 */
class FeatureExtractor {
    private static final Logger logger = Logger.getLogger(FeatureExtractor.class.getName());
    
    private List<String> featureNames = Arrays.asList(
        "domChangeDistance",
        "attributeSimilarity",
        "pathSimilarity",
        "textSimilarity",
        "classSimilarity",
        "parentPathSimilarity",
        "visibilitySimilarity",
        "positionSimilarity",
        "tagNameMatch",
        "placeholderMatch",
        "typeMatch",
        "nameMatch",
        "dataAttributeMatch"
    );

    public double[] extractFeatureVector(Map<String, Double> features) {
        double[] vector = new double[featureNames.size()];
        
        for (int i = 0; i < featureNames.size(); i++) {
            String featureName = featureNames.get(i);
            vector[i] = features.getOrDefault(featureName, 0.0);
        }
        
        // Normalize features
        normalizeVector(vector);
        
        return vector;
    }

    /**
     * Normalize feature vector to [0, 1] range
     */
    private void normalizeVector(double[] vector) {
        double max = 0;
        for (double v : vector) {
            max = Math.max(max, v);
        }
        
        if (max > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] = vector[i] / max;
            }
        }
    }

    public List<String> getFeatureNames() {
        return featureNames;
    }
}

/**
 * Random Forest Classifier: Placeholder for SMILE Random Forest implementation
 * In production, use: com.github.haifengl.smile.classification.RandomForest
 */
class RandomForestClassifier {
    private static final Logger logger = Logger.getLogger(RandomForestClassifier.class.getName());
    
    // In real implementation:
    // private RandomForest model;
    // Using SMILE library: com.github.haifengl.smile.classification.RandomForest
    
    public void train(double[][] X, int[] y, int numTrees) {
        logger.info("Training Random Forest with " + numTrees + " trees");
        logger.info("Training set size: " + X.length);
        logger.info("Number of features: " + X[0].length);
        
        // In production:
        // this.model = RandomForest.fit(X, y, numTrees);
    }

    public int predict(double[] x) {
        // Return mock prediction for demonstration
        double max = 0;
        int maxIndex = 0;
        for (int i = 0; i < x.length; i++) {
            if (x[i] > max) {
                max = x[i];
                maxIndex = i;
            }
        }
        return maxIndex % 5; // 5 strategies
    }

    public double[] predictProba(double[] x) {
        double[] proba = new double[5];
        double sum = 0;
        
        for (int i = 0; i < 5; i++) {
            proba[i] = Math.random();
            sum += proba[i];
        }
        
        // Normalize to probability distribution
        for (int i = 0; i < 5; i++) {
            proba[i] /= sum;
        }
        
        return proba;
    }

    public void save(String path) {
        logger.info("Saving model to: " + path);
        // In production: SMILE's serialization
    }

    public void load(String path) {
        logger.info("Loading model from: " + path);
        // In production: SMILE's deserialization
    }

    public Map<String, Double> getFeatureImportance(List<String> featureNames) {
        Map<String, Double> importance = new LinkedHashMap<>();
        for (String name : featureNames) {
            importance.put(name, Math.random());
        }
        return importance;
    }
}
