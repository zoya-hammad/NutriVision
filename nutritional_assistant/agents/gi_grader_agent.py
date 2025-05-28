import anthropic
import os
from dotenv import load_dotenv
from typing import Dict, List, Any
import json
import re
from .base_agent import BaseAgent

class GIGraderAgent(BaseAgent):
    """
    Agent responsible for evaluating the accuracy of GI predictions using an ensemble of Claude 3 models.
    Uses both Haiku and Sonnet models for more accurate GI predictions.
    """
    
    def __init__(self):
        """Initialize the GI grader agent with ensemble models"""
        super().__init__(name="GI Grader Agent")
        load_dotenv()
        self.client = anthropic.Anthropic(api_key=os.getenv('ANTHROPIC_API_KEY'))
        self.primary_model = "claude-3-haiku-20240307"
        self.secondary_model = "claude-3-5-haiku-20241022"
    
    def extract_gi_value(self, text: str) -> float:
        """
        Extract GI value from text using regex patterns.
        Handles various formats like "around 30-40", "about 35", "GI of 45", etc.
        """
        # Try to find a range first (e.g., "30-40")
        range_match = re.search(r'(\d+)\s*-\s*(\d+)', text)
        if range_match:
            # Take the average of the range
            low = float(range_match.group(1))
            high = float(range_match.group(2))
            return (low + high) / 2
            
        # Try to find a single number
        number_match = re.search(r'(?:GI|glycemic index|index|value|around|about|approximately)?\s*(?:of|is|:)?\s*(\d+(?:\.\d+)?)', text, re.IGNORECASE)
        if number_match:
            return float(number_match.group(1))
            
        # If no number found, return default
        return 50.0
    
    def get_model_prediction(self, prompt: str, model: str) -> float:
        """Get prediction from a single model."""
        try:
            response = self.client.messages.create(
                model=model,
                max_tokens=50,
                temperature=0.1,
                messages=[{"role": "user", "content": prompt}]
            )
            
            response_text = response.content[0].text.strip()
            return self.extract_gi_value(response_text)
            
        except Exception as e:
            print(f"Error in get_model_prediction for {model}: {str(e)}")
            return 50.0
    
    def grade_prediction(self, recipe: Dict[str, Any], predicted_gi: float) -> Dict[str, Any]:
        """
        Grade the accuracy of our GI prediction for a recipe using an ensemble of Claude models.
        
        Args:
            recipe (Dict[str, Any]): Recipe dictionary containing title, ingredients, etc.
            predicted_gi (float): Our system's predicted GI value
            
        Returns:
            Dict[str, Any]: Grading results
        """
        try:
            # Create prompt for expert grading
            prompt = f"""As a nutrition expert, assess the glycemic index (GI) of this recipe:
            Title: {recipe['title']}
            Ingredients: {', '.join(recipe['ingredients'])}
            
            Our system predicted a GI of {predicted_gi:.1f}.
            What is the actual GI value of this recipe? Return only a number between 0 and 100."""

            # Get predictions from both models
            primary_gi = self.get_model_prediction(prompt, self.primary_model)
            secondary_gi = self.get_model_prediction(prompt, self.secondary_model)
            
            # Simple average of both predictions
            assessed_gi = (primary_gi + secondary_gi) / 2
            
            # Calculate difference
            prediction_difference = abs(assessed_gi - predicted_gi)
            
            return {
                "assessed_gi": assessed_gi,
                "prediction_difference": prediction_difference,
                "primary_gi": primary_gi,
                "secondary_gi": secondary_gi
            }
            
        except Exception as e:
            print(f"Error in grade_prediction: {str(e)}")
            return {
                "assessed_gi": 50.0,
                "prediction_difference": abs(50.0 - predicted_gi),
                "primary_gi": 50.0,
                "secondary_gi": 50.0
            }