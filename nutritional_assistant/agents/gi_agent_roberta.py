from typing import Tuple
import requests
import pandas as pd
from huggingface_hub import InferenceClient
import numpy as np
from typing import Dict, List, Any
from .base_agent import BaseAgent
import os
from dotenv import load_dotenv
import json
import openai

import torch
from transformers import AutoTokenizer, AutoModelForSequenceClassification

class GIAnalysisAgentRoBERTa(BaseAgent):
    """
    Agent responsible for analyzing recipes and determining their glycemic impact.
    Uses a combination of database lookup and RoBERTa model prediction for GI values.
    """
    
    def __init__(self):
        """Initialize the GI analysis agent"""
        super().__init__(
            name="GI Analysis Agent",
            color=self.BLUE
        )
        
        # Load food database
        self.food_data = self._load_food_data()
        load_dotenv()
        self.api_endpoint = os.getenv('ROBERTA_API_ENDPOINT')
        self.hf_token = os.getenv('HF_TOKEN')
        if not self.api_endpoint:
            raise ValueError("ROBERTA_API_ENDPOINT not found in environment variables")
        
    def _load_food_data(self) -> pd.DataFrame:
        """Load food nutritional data"""
        food_data_path = os.path.join(os.path.dirname(os.path.dirname(__file__)), 
                                    "data preprocessing", 
                                    "processed_data", 
                                    "cleaned_diabetic_foods.csv")
        try:
            df = pd.read_csv(food_data_path)
            # Ensure required columns exist
            required_columns = ['Food Name', 'Glycemic Index']
            if not all(col in df.columns for col in required_columns):
                raise ValueError(f"Missing required columns. Found: {df.columns.tolist()}")
            
            # Clean data
            df['Food Name'] = df['Food Name'].str.strip()
            df['Glycemic Index'] = pd.to_numeric(df['Glycemic Index'], errors='coerce')
            
            print(f"Successfully loaded food data with {len(df)} entries")
            return df
        except FileNotFoundError:
            print(f"Food data file not found at {food_data_path}")
            return pd.DataFrame()
        except Exception as e:
            print(f"Error loading food data: {str(e)}")
            return pd.DataFrame()

    def extract_ingredients(self, recipe: Dict[str, Any]) -> List[Dict[str, Any]]:
        """
        Extract ingredients and quantities from a recipe using GPT.
        
        Args:
            recipe (Dict[str, Any]): Recipe dictionary containing title, ingredients, etc.
            
        Returns:
            List[Dict[str, Any]]: List of dictionaries containing ingredient details
        """
        try:
            # Create prompt for ingredient extraction
            prompt = f"""Extract ingredients and their quantities from this recipe:
            Title: {recipe['title']}
            Ingredients: {', '.join(recipe['ingredients'])}
            
            Return a JSON array of objects with the following format:
            [
                {{
                    "ingredient": "ingredient name",
                    "quantity": "amount",
                    "unit": "unit of measurement"
                }}
            ]
            Only include the JSON array in your response. Do not include any other text."""

            # Get response from OpenAI
            response = openai.chat.completions.create(
                model="gpt-4o-mini",
                messages=[
                    {"role": "system", "content": "You are a helpful assistant that extracts ingredients from recipes."},
                    {"role": "user", "content": prompt}
                ],
                temperature=0.1,
                max_tokens=512
            )
            
            # Get the response content and clean it
            content = response.choices[0].message.content.strip()
            
            # Try to find JSON array in the response
            try:
                # First try direct parsing
                ingredients = json.loads(content)
            except json.JSONDecodeError:
                # If that fails, try to extract JSON array from the text
                import re
                json_match = re.search(r'\[.*\]', content, re.DOTALL)
                if json_match:
                    try:
                        ingredients = json.loads(json_match.group())
                    except json.JSONDecodeError:
                        print(f"Failed to parse JSON from response: {content}")
                        return []
                else:
                    print(f"No JSON array found in response: {content}")
                    return []
            
            # Validate the parsed ingredients
            if not isinstance(ingredients, list):
                print(f"Expected list but got {type(ingredients)}")
                return []
                
            # Validate each ingredient object
            valid_ingredients = []
            for item in ingredients:
                if isinstance(item, dict) and all(k in item for k in ['ingredient', 'quantity', 'unit']):
                    valid_ingredients.append(item)
                else:
                    print(f"Invalid ingredient format: {item}")
            
            if not valid_ingredients:
                print("No valid ingredients found in response")
                return []
                
            return valid_ingredients
            
        except Exception as e:
            print(f"Error extracting ingredients: {str(e)}")
            print(f"Recipe title: {recipe.get('title', 'Unknown')}")
            print(f"Raw ingredients: {recipe.get('ingredients', [])}")
            return []

    def sanitize_gi_response(self, response: str) -> float:
        """
        Sanitize the LLM response to ensure we get a valid GI value.
        
        Args:
            response (str): Raw response from LLM
            
        Returns:
            float: Sanitized GI value between 0 and 100
        """
        try:
            # Remove any non-numeric characters except decimal point
            cleaned = ''.join(c for c in response if c.isdigit() or c == '.')
            
            # Convert to float
            gi_value = float(cleaned)
            
            # Ensure value is between 0 and 100
            gi_value = int(max(0.0, min(100.0, gi_value)))
            
            return gi_value
            
        except (ValueError, TypeError):
            print(f"Error sanitizing GI response: {response}")
            return 50.0  # Default to middle value if sanitization fails

    

    def calculate_glycemic_load(self, ingredients: List[Dict[str, Any]]) -> float:
        """
        Calculate the total glycemic load of a recipe.
        
        Args:
            ingredients (List[Dict[str, Any]]): List of ingredient dictionaries
            
        Returns:
            float: Total glycemic load
        """
        total_load = 0.0
        
        for ingredient in ingredients:
            try:
                # Get GI value
                gi_value = self.get_gi_value(ingredient['ingredient'])
                
                # Get carbohydrate content using LLM
                prompt = f"""What is the carbohydrate content in grams per 100g for {ingredient['ingredient']}?
                Return only a number. If uncertain, return 0."""
                
                response = openai.chat.completions.create(
                    model="gpt-4o-mini",
                    messages=[{"role": "user", "content": prompt}],
                    temperature=0.1
                )
                
                # Extract and sanitize carb content
                carb_content = response.choices[0].message.content.strip()
                try:
                    # Remove any non-numeric characters except decimal point
                    carb_content = ''.join(c for c in carb_content if c.isdigit() or c == '.')
                    carb_content = float(carb_content) if carb_content else 0.0
                except (ValueError, TypeError):
                    print(f"Error parsing carb content for {ingredient['ingredient']}, defaulting to 0")
                    carb_content = 0.0
                
                # Convert quantity to grams if needed
                quantity = float(ingredient['quantity'])
                unit = ingredient['unit'].lower()
                
                # Convert to grams if not already
                if unit in ['kg', 'kilo', 'kilogram']:
                    quantity *= 1000
                elif unit in ['oz', 'ounce']:
                    quantity *= 28.35
                elif unit in ['lb', 'pound']:
                    quantity *= 453.59
                elif unit in ['cup', 'cups']:
                    quantity *= 240  # Approximate conversion
                elif unit in ['tbsp', 'tablespoon']:
                    quantity *= 15
                elif unit in ['tsp', 'teaspoon']:
                    quantity *= 5
                
                # Calculate GL for this ingredient
                # GL = (GI × carbohydrate content in grams) / 100
                ingredient_load = (gi_value * (carb_content * quantity / 100)) / 100
                total_load += ingredient_load
                
                print(f"Ingredient: {ingredient['ingredient']}")
                print(f"GI: {gi_value}, Carbs: {carb_content}g/100g, Quantity: {quantity}g")
                print(f"GL contribution: {ingredient_load:.2f}")
                
            except (ValueError, TypeError) as e:
                print(f"Error calculating GL for {ingredient['ingredient']}: {str(e)}")
                continue
                
        return total_load
    
    def get_gi_value(self, ingredient: str) -> float:
        """
        Get GI value for an ingredient from database or RoBERTa model.
        
        Args:
            ingredient (str): Name of the ingredient
            
        Returns:
            float: GI value
        """
        # Clean ingredient name
        ingredient = ingredient.lower().strip()
        
        # Check if ingredient exists in database
        if not self.food_data.empty:
            # Try exact match
            match = self.food_data[self.food_data['Food Name'].str.lower() == ingredient]
            if not match.empty:
                gi_value = match.iloc[0]['Glycemic Index']
                if pd.notna(gi_value) and isinstance(gi_value, (int, float)):
                    return float(gi_value)
        
        try:
            # If not in database, use RoBERTa model
            prompt = f"The glycemic index of {ingredient} is <mask>."
            
            # Make API call to RoBERTa
            headers = {
                "Content-Type": "application/json",
                "Authorization": f"Bearer {self.hf_token}"
            }
            
            payload = {
                "inputs": prompt,
            }
            
            response = requests.post(
                self.api_endpoint,
                headers=headers,
                json=payload,
                timeout=10
            )
            
            if response.status_code == 200:
                result = response.json()
                
                # Handle list response
                if isinstance(result, list) and result:
                    result = result[0]
                
                # Map RoBERTa's text prediction to GI values
                if 'token_str' in result:
                    token = result['token_str'].strip().lower()
                    
                    # Map descriptive terms to approximate GI values
                    gi_mapping = {
                        'very low': 20,
                        'low': 35,
                        'moderate': 55, 
                        'medium': 55,
                        'high': 70,
                        'very high': 85,
                        'extremely high': 95,
                        'minimal': 15,
                        'slight': 25,
                        'significant': 65
                    }
                    
                    # Check for matches in our mapping
                    for term, value in gi_mapping.items():
                        if term in token:
                            return value
                    
                    # Additional mappings for numeric values
                    if token.replace('.', '', 1).isdigit():
                        gi_value = float(token)
                        if 0 <= gi_value <= 100:  # Valid GI range
                            return gi_value
                    
                    # Default based on common categories
                    if any(word in token for word in ['small', 'little', 'minimal', 'tiny']):
                        return 30.0
                    elif any(word in token for word in ['average', 'normal', 'moderate']):
                        return 50.0
                    elif any(word in token for word in ['large', 'big', 'substantial']):
                        return 70.0
                    
                    # Final fallback
                    return 50.0
            
            # If API call fails, return default
            return 50.0
            
        except (requests.RequestException, ValueError, TypeError) as e:
            print(f"Error getting GI value for {ingredient}: {str(e)}")
            return 50.0
        
        # Default value for any other errors
        return 50.0

    def analyze_glycemic_load(self, glycemic_load: float) -> Dict[str, Any]:
        """
        Analyze and categorize the glycemic load of a recipe.
        
        Args:
            glycemic_load (float): The calculated glycemic load value
            
        Returns:
            Dict[str, Any]: Analysis results including category and recommendations
        """
        # GL Categories:
        # Low: 0-10
        # Medium: 11-19
        # High: 20+
        
        if glycemic_load <= 10:
            category = "Low"
            impact = "Minimal impact on blood sugar"
            recommendation = "This recipe is suitable for maintaining stable blood sugar levels"
        elif glycemic_load <= 19:
            category = "Medium"
            impact = "Moderate impact on blood sugar"
            recommendation = "Consider pairing with protein or fiber to slow absorption"
        else:
            category = "High"
            impact = "Significant impact on blood sugar"
            recommendation = "Consider reducing portion size or balancing with low-GI foods"
            
        return {
            "category": category,
            "glycemic_load": glycemic_load,
            "impact": impact,
            "recommendation": recommendation
        }

    def process(self, recipes: List[Dict[str, Any]]) -> Dict[str, Any]:
        """
        Process recipes and return the one with lowest glycemic load.
        
        Args:
            recipes (List[Dict[str, Any]]): List of recipe dictionaries
            
        Returns:
            Dict[str, Any]: Selected recipe with GI analysis
        """
        try:
            best_recipe = None
            lowest_load = float('inf')
            
            for recipe in recipes:
                # Extract ingredients
                ingredients = self.extract_ingredients(recipe)
                if not ingredients:
                    continue
                
                # Calculate glycemic load
                glycemic_load = self.calculate_glycemic_load(ingredients)
                
                # Analyze glycemic load
                gl_analysis = self.analyze_glycemic_load(glycemic_load)
                
                # Update best recipe if this one has lower load
                if glycemic_load < lowest_load:
                    lowest_load = glycemic_load
                    best_recipe = {
                        'title': recipe['title'],
                        'ingredients': ingredients,
                        'instructions': recipe['instructions'],
                        'glycemic_load': glycemic_load,
                        'gl_analysis': gl_analysis
                    }
            
            if not best_recipe:
                return {"error": "No valid recipes found for GI analysis"}
            
            return best_recipe
            
        except Exception as e:
            print(f"Error processing recipes: {str(e)}")
            return {"error": str(e)} 