import os
import json
from typing import Dict, Any
from dotenv import load_dotenv
import openai

class GPTAnalysisAgent:
    def __init__(self):
        """Initialize the GPT Analysis Agent."""
        load_dotenv()
        self.model = "gpt-4o-mini"
        
    def analyze_recipe(self, recipe: Dict[str, Any]) -> Dict[str, Any]:
        """
        Analyze a recipe using GPT to get glycemic load and analysis.
        
        Args:
            recipe: Dictionary containing recipe details
                {
                    'title': str,
                    'ingredients': List[str],
                    'instructions': List[str]
                }
                
        Returns:
            Dictionary containing GL analysis
            {
                'glycemic_load': float,
                'gl_analysis': {
                    'category': str,
                    'impact': str,
                    'recommendation': str
                }
            }
        """
        try:
            # Create prompt for GPT
            prompt = f"""Analyze the glycemic load of this recipe:
            Title: {recipe['title']}
            Ingredients: {', '.join(recipe['ingredients'])}
            Instructions: {' '.join(recipe['instructions'])}
            
            Return a JSON object with the following structure:
            {{
                "glycemic_load": float,
                "gl_analysis": {{
                    "category": "Low/Medium/High",
                    "impact": "Impact description",
                    "recommendation": "Recommendation for consumption"
                }}
            }}
            Only return the JSON object, no other text."""

            # Get response from GPT
            response = openai.chat.completions.create(
                model=self.model,
                messages=[{"role": "user", "content": prompt}],
                temperature=0.1
            )
            
            # Parse and return the response
            return json.loads(response.choices[0].message.content.strip())
            
        except Exception as e:
            print(f"Error analyzing recipe {recipe['title']}: {str(e)}")
            return None 