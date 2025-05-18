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
    
    def grade_prediction(self, recipe: Dict[str, Any], our_prediction: float) -> Dict[str, Any]:
        """
        Grade the accuracy of our GI prediction for a recipe.
        
        Args:
            recipe (Dict[str, Any]): Recipe dictionary containing title, ingredients, etc.
            our_prediction (float): Our system's predicted GI value
            
        Returns:
            Dict[str, Any]: Grading results including assessed GI, confidence, and feedback
        """
        try:
            # Format ingredients list for better readability
            ingredients_str = "\n".join([f"- {ing}" for ing in recipe['ingredients']])
            
            # Create detailed prompt for the LLM
            prompt = f"""
            As a nutrition expert, analyze this recipe and its predicted glycemic index:
            
            Recipe Title: {recipe['title']}
            
            Ingredients:
            {ingredients_str}
            
            Cooking Instructions:
            {recipe['instructions']}
            
            Our Predicted GI: {our_prediction}
            
            Please provide a comprehensive analysis including:
            1. Your assessment of the recipe's glycemic impact (0-100)
            2. Key factors affecting the GI (e.g., cooking method, ingredient combinations)
            3. Specific suggestions for improving the recipe's glycemic impact
            4. Analysis of any discrepancy between your assessment and our prediction
            
            Format your response as a JSON object with the following structure:
            {{
                "assessed_gi": float,
                "factors": [str],
                "suggestions": [str],
                "discrepancy_analysis": str,
                "explanation": str
            }}
            
            Focus on:
            - Ingredient interactions and their combined effect
            - Cooking method impact on GI
            - Portion sizes and their influence
            - Overall meal composition
            """
            
            # Get response from LLM
            response = openai.chat.completions.create(
                model="gpt-4o-mini",  # Using GPT-4 for more accurate analysis
                messages=[{"role": "user", "content": prompt}],
                temperature=0.3  # Lower temperature for more consistent results
            )
            
            # Parse the response
            result = json.loads(response.choices[0].message.content)
            
            # Add metadata
            result['our_prediction'] = our_prediction
            result['prediction_difference'] = abs(result['assessed_gi'] - our_prediction)
            
            return result
            
        except Exception as e:
            print(f"Error grading prediction: {str(e)}")
            return {
                "error": str(e),
                "assessed_gi": 50.0,
                "factors": ["Error in grading"],
                "suggestions": ["System error occurred"],
                "discrepancy_analysis": "Unable to analyze",
                "explanation": "Error occurred during grading",
                "our_prediction": our_prediction,
                "prediction_difference": 0.0
            }
    
    def batch_grade(self, recipes: List[Dict[str, Any]], predictions: List[float]) -> List[Dict[str, Any]]:
        """
        Grade multiple recipes and their predictions.
        
        Args:
            recipes (List[Dict[str, Any]]): List of recipe dictionaries
            predictions (List[float]): List of our predicted GI values
            
        Returns:
            List[Dict[str, Any]]: List of grading results
        """
        results = []
        for recipe, prediction in zip(recipes, predictions):
            result = self.grade_prediction(recipe, prediction)
            results.append(result)
        return results 