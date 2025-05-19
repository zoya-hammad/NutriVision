import openai
import os
from dotenv import load_dotenv
from typing import Dict, List, Any
import json
from .base_agent import BaseAgent

class GIGraderAgent(BaseAgent):
    """
    Agent responsible for evaluating the accuracy of GI predictions using LLM.
    Acts as an expert grader to assess the quality of our GI predictions.
    """
    
    def __init__(self):
        """Initialize the GI grader agent"""
        super().__init__(
            name="GI Grader Agent",
            color=self.GREEN
        )
        load_dotenv()
        openai.api_key = os.getenv('OPENAI_API_KEY')
    
    def grade_prediction(self, recipe: Dict[str, Any], predicted_gi: float) -> Dict[str, Any]:
        """
        Grade the accuracy of our GI prediction for a recipe.
        
        Args:
            recipe (Dict[str, Any]): Recipe dictionary containing title, ingredients, etc.
            our_prediction (float): Our system's predicted GI value
            
        Returns:
            Dict[str, Any]: Grading results
        """
        try:
            # Create prompt for expert assessment
            prompt = f"""As a nutrition expert, assess the glycemic index (GI) for this recipe:
            Title: {recipe['title']}
            Ingredients: {', '.join(recipe['ingredients'])}
            
            Return only a number between 0 and 100 representing the expected GI value.
            Base your assessment on the ingredients and their typical GI values."""
            
            # Get expert assessment
            response = openai.chat.completions.create(
                model="gpt-4o-mini",
                messages=[{"role": "user", "content": prompt}],
                temperature=0.1
            )
            
            # Extract and sanitize expert assessment
            expert_gi = float(''.join(c for c in response.choices[0].message.content.strip() 
                                    if c.isdigit() or c == '.'))
            
            # Calculate prediction difference
            prediction_difference = abs(expert_gi - predicted_gi)
            
            return {
                'assessed_gi': expert_gi,
                'prediction_difference': prediction_difference
            }
            
        except Exception as e:
            print(f"Error grading prediction: {str(e)}")
            return {
                'assessed_gi': 50.0,  # Default to middle value
                'prediction_difference': abs(50.0 - predicted_gi)
            }