import pandas as pd
import numpy as np
from typing import Dict, List, Any
from .base_agent import BaseAgent
import openai
import os
from dotenv import load_dotenv

class GlycemicIndexAgent(BaseAgent):
    """
    Agent responsible for analyzing recipes for glycemic load and nutritional content.
    Uses the cleaned_diabetic_foods.csv data to calculate glycemic load and nutritional values.
    Falls back to LLM-based estimation for unknown ingredients.
    """
    
    def __init__(self):
        """Initialize the GI analysis agent"""
        super().__init__(
            name="Glycemic Index Agent",
            color=self.BLUE
        )
        self.food_data = self._load_food_data()
        load_dotenv()
        openai.api_key = os.getenv('OPENAI_API_KEY')
        
    def _load_food_data(self) -> pd.DataFrame:
        """Load food nutritional data"""
        food_data_path = "../data preprocessing/processed_data/cleaned_diabetic_foods.csv"
        try:
            df = pd.read_csv(food_data_path)
            # Ensure required columns exist
            required_cols = ['Food Name', 'Glycemic Index','Calories', 'Carbohydrates', 'Protein', 'Fat', 'Fiber Content']
            if not all(col in df.columns for col in required_cols):
                self.log("Warning: Some required columns are missing from food data")
            return df
        except FileNotFoundError:
            self.log(f"Food data file not found at {food_data_path}")
            return pd.DataFrame()

    def _estimate_nutrition_with_llm(self, ingredient: str) -> Dict[str, Any]:
        """Estimate nutritional values for an ingredient using LLM"""
        try:
            self.log(f"Starting LLM estimation for: {ingredient}")
            prompt = f"""You are a nutritional expert. Provide estimated nutritional values for {ingredient} per 100g serving.
            Return ONLY a valid JSON object with these exact keys and numeric values (no other text):
            {{
                "Food Name": "{ingredient}",
                "Glycemic Index": <number between 0-100>,
                "Calories": <number>,
                "Carbohydrates": <number in grams>,
                "Protein": <number in grams>,
                "Fat": <number in grams>,
                "Fiber Content": <number in grams>
            }}
            Use realistic estimates based on common nutritional knowledge. Ensure all values are numbers, not strings."""

            self.log("Sending request to LLM...")
            response = openai.ChatCompletion.create(
                model="gpt-4o-mini",
                messages=[
                    {"role": "system", "content": "You are a nutritional expert. Provide accurate estimates in valid JSON format only."},
                    {"role": "user", "content": prompt}
                ],
                temperature=0.1  # Lower temperature for more consistent output
            )
            
            # Parse the response to get the JSON object
            import json
            try:
                self.log("Received response from LLM, attempting to parse JSON...")
                content = response.choices[0].message.content.strip()
                # Remove any markdown code block markers if present
                content = content.replace('```json', '').replace('```', '').strip()
                nutrition_data = json.loads(content)
                
                # Validate the data
                required_keys = ["Food Name", "Glycemic Index", "Calories", "Carbohydrates", "Protein", "Fat", "Fiber Content"]
                if not all(key in nutrition_data for key in required_keys):
                    self.log(f"Missing required keys in LLM response: {nutrition_data}")
                    return None
                    
                # Ensure all values are numbers
                for key in required_keys:
                    if key != "Food Name" and not isinstance(nutrition_data[key], (int, float)):
                        self.log(f"Invalid value type for {key}: {nutrition_data[key]}")
                        return None
                
                self.log(f"Successfully parsed LLM response: {nutrition_data}")
                return nutrition_data
            except json.JSONDecodeError as e:
                self.log(f"Failed to parse LLM response: {str(e)}")
                self.log(f"Raw response: {content}")
                return None
                
        except Exception as e:
            self.log(f"Error in LLM estimation: {str(e)}")
            return None

    def _extract_ingredient_name(self, ingredient: str) -> str:
        """Extract the main ingredient name from a string containing quantities and units"""
        # Remove common quantity indicators
        words = ingredient.lower().split()
        # Skip words that are likely quantities or units
        skip_words = {'cup', 'cups', 'tablespoon', 'tablespoons', 'tbsp', 'teaspoon', 'teaspoons', 'tsp', 
                     'ounce', 'ounces', 'oz', 'pound', 'pounds', 'lb', 'gram', 'grams', 'g', 'ml', 'milliliter',
                     'milliliters', 'l', 'liter', 'liters', 'pinch', 'dash', 'to', 'taste', 'or', 'and'}
        
        # Remove numbers and units
        ingredient_words = [word for word in words if not word.isdigit() and word not in skip_words]
        
        # Join the remaining words
        return ' '.join(ingredient_words)

    def _find_food_match(self, ingredient: str) -> Dict[str, Any]:
        """Find the best match for an ingredient in the food database or estimate with LLM"""
        try:
            # Extract the main ingredient name
            ingredient_name = self._extract_ingredient_name(ingredient)
            self.log(f"Extracted ingredient name: {ingredient_name}")
            
            # Try exact match
            exact_match = self.food_data[self.food_data['Food Name'].str.lower() == ingredient_name.lower()]
            if not exact_match.empty:
                self.log(f"Found exact match for {ingredient_name}")
                return exact_match.iloc[0].to_dict()
            
            # If no exact match found, use LLM to estimate nutritional values
            self.log(f"No exact match found for {ingredient_name}, using LLM estimation")
            estimated_nutrition = self._estimate_nutrition_with_llm(ingredient_name)
            if estimated_nutrition:
                self.log(f"Successfully got LLM estimation for {ingredient_name}")
                # Scale the nutritional values based on the quantity in the ingredient string
                scaled_nutrition = self._scale_nutritional_values(estimated_nutrition, ingredient)
                self.log(f"Scaled nutrition values: {scaled_nutrition}")
                return scaled_nutrition
            else:
                self.log(f"LLM estimation failed for {ingredient_name}")
            
            return None
            
        except Exception as e:
            self.log(f"Error finding food match for {ingredient}: {str(e)}")
            return None

    def _scale_nutritional_values(self, nutrition_data: Dict[str, Any], ingredient: str) -> Dict[str, Any]:
        """Scale nutritional values based on the quantity in the ingredient string"""
        try:
            # Extract quantity and unit
            words = ingredient.lower().split()
            quantity = 1.0
            unit = None
            
            # Find the first number in the string
            for i, word in enumerate(words):
                try:
                    quantity = float(word)
                    # Check if next word is a unit
                    if i + 1 < len(words):
                        unit = words[i + 1]
                    break
                except ValueError:
                    continue
            
            # Define unit conversion factors (per 100g)
            unit_conversions = {
                'cup': 240,  # 1 cup = 240g
                'cups': 240,
                'tablespoon': 15,  # 1 tbsp = 15g
                'tablespoons': 15,
                'tbsp': 15,
                'teaspoon': 5,  # 1 tsp = 5g
                'teaspoons': 5,
                'tsp': 5,
                'ounce': 28.35,  # 1 oz = 28.35g
                'ounces': 28.35,
                'oz': 28.35,
                'pound': 453.59,  # 1 lb = 453.59g
                'pounds': 453.59,
                'lb': 453.59
            }
            
            # Calculate scaling factor
            scaling_factor = 1.0
            if unit and unit in unit_conversions:
                scaling_factor = (quantity * unit_conversions[unit]) / 100.0
            
            # Scale all nutritional values
            scaled_data = nutrition_data.copy()
            for key in ['Calories', 'Carbohydrates', 'Protein', 'Fat', 'Fiber Content']:
                if key in scaled_data:
                    scaled_data[key] = round(scaled_data[key] * scaling_factor, 2)
            
            return scaled_data
            
        except Exception as e:
            self.log(f"Error scaling nutritional values: {str(e)}")
            return nutrition_data

    def _calculate_recipe_nutrition(self, recipe: Dict[str, Any]) -> Dict[str, Any]:
        """
        Calculate nutritional information for a recipe.
        Returns total calories, protein, carbs, fat, fiber, and glycemic load.
        """
        total_calories = 0
        total_protein = 0
        total_carbs = 0
        total_fat = 0
        total_fiber = 0
        total_gi = 0
        total_carbs_gi = 0
        matched_ingredients = []
        unmatched_ingredients = []
        
        for ingredient in recipe['ingredients']:
            food_info = self._find_food_match(ingredient)
            if food_info:
                # Add nutritional values (using correct case for keys)
                total_calories += food_info.get('Calories', 0)
                total_protein += food_info.get('Protein', 0)
                total_carbs += food_info.get('Carbohydrates', 0)
                total_fat += food_info.get('Fat', 0)
                total_fiber += food_info.get('Fiber Content', 0)
                
                # Calculate glycemic load contribution
                gi = food_info.get('Glycemic Index', 0)
                carbs = food_info.get('Carbohydrates', 0)
                total_gi += gi
                total_carbs_gi += (gi * carbs) / 100
                
                matched_ingredients.append({
                    'ingredient': ingredient,
                    'nutrition': food_info
                })
            else:
                unmatched_ingredients.append(ingredient)
                self.log(f"Could not find nutritional information for: {ingredient}")
        
        # Calculate glycemic load
        glycemic_load = total_carbs_gi / 100 if total_carbs > 0 else 0
        
        return {
            'total_calories': round(total_calories, 2),
            'total_protein': round(total_protein, 2),
            'total_carbs': round(total_carbs, 2),
            'total_fat': round(total_fat, 2),
            'total_fiber': round(total_fiber, 2),
            'average_gi': round(total_gi / len(recipe['ingredients']) if recipe['ingredients'] else 0, 2),
            'glycemic_load': round(glycemic_load, 2),
            'matched_ingredients': matched_ingredients,
            'unmatched_ingredients': unmatched_ingredients
        }

    def process(self, input_data: Dict[str, Any]) -> Dict[str, Any]:
        """
        Process recipes and return nutritional analysis with glycemic load.
        
        Args:
            input_data (Dict[str, Any]): Dictionary containing recipes and their information
            
        Returns:
            Dict[str, Any]: Dictionary containing analyzed recipes and recommendations
        """
        self.log("Starting recipe analysis")
        
        if 'error' in input_data:
            return input_data
            
        try:
            recipes = input_data.get('recipes', [])
            if not recipes:
                return {"error": "No recipes provided for analysis"}
                
            # Analyze each recipe
            analyzed_recipes = []
            for recipe in recipes:
                nutrition = self._calculate_recipe_nutrition(recipe)
                analyzed_recipe = {
                    'title': recipe['title'],
                    'ingredients': recipe['ingredients'],
                    'instructions': recipe['instructions'],
                    'nutrition': nutrition
                }
                analyzed_recipes.append(analyzed_recipe)
            
            # Find recipe with lowest glycemic load
            best_recipe = min(analyzed_recipes, 
                            key=lambda x: x['nutrition']['glycemic_load'])
            
            self.log("Recipe analysis completed successfully")
            
            return {
                'analyzed_recipes': analyzed_recipes,
                'best_recipe': best_recipe,
                'recommendation': f"Based on glycemic load analysis, '{best_recipe['title']}' is the best choice with a glycemic load of {best_recipe['nutrition']['glycemic_load']}."
            }
            
        except Exception as e:
            self.log(f"Error analyzing recipes: {str(e)}")
            return {"error": str(e)}