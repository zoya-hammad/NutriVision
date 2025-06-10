from typing import Tuple
import requests
import pandas as pd
import numpy as np
from typing import Dict, List, Any
from .base_agent import BaseAgent
import os
from dotenv import load_dotenv
import json
import openai
import torch
from transformers import pipeline, AutoTokenizer, AutoModelForSequenceClassification
from huggingface_hub import login

class GIAnalysisAgentRoBERTaFinetuned2(BaseAgent):
    """
    Agent responsible for analyzing recipes and determining their glycemic impact.
    Uses a combination of database lookup and finetuned RoBERTa model prediction for GI values.
    """
    
    def __init__(self):
        """Initialize the GI analysis agent"""
        super().__init__(name="GI Analysis Agent (Finetuned 2)")
        
        # Load food database
        self.food_data = self._load_food_data()
        load_dotenv()
        
        # Login to Hugging Face
        hf_token = os.getenv('HF_TOKEN')
        login(token=hf_token)
        
        # Load tokenizer and model
        self.tokenizer = AutoTokenizer.from_pretrained("roberta-base")
        self.model = AutoModelForSequenceClassification.from_pretrained("zoya-hammadk/nutrivision-roberta-25")
        
        # Move model to GPU if available
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        self.model = self.model.to(self.device)
        self.model.eval()
        
        # Define high GI ingredients to look for in titles
        self.HIGH_GI_INGREDIENTS = [
            'oats', 'oatmeal', 'rice', 
            'pasta', 'bread', 'flour', 'yoghurt', 'honey', 'maple syrup',
            'corn', 'cornmeal', 'pancake', 'waffle', 'muffin',
            'cake', 'cookie', 'quinoa', 'toast', 'potatoes', 'white bread'
        ]
        
        # Define GI ranges for each class
        self.gi_ranges = {
            0: 2.0,   # 0-4
            1: 6.0,   # 5-8
            2: 10.0,  # 9-12
            3: 14.0,  # 13-16
            4: 18.0,  # 17-20
            5: 22.0,  # 21-24
            6: 26.0,  # 25-28
            7: 30.0,  # 29-32
            8: 34.0,  # 33-36
            9: 38.0,  # 37-40
            10: 42.0, # 41-44
            11: 46.0, # 45-48
            12: 50.0, # 49-52
            13: 54.0, # 53-56
            14: 58.0, # 57-60
            15: 62.0, # 61-64
            16: 66.0, # 65-68
            17: 70.0, # 69-72
            18: 74.0, # 73-76
            19: 78.0, # 77-80
            20: 82.0, # 81-84
            21: 86.0, # 85-88
            22: 90.0, # 89-92
            23: 94.0, # 93-96
            24: 98.0  # 97-100
        }
        
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

    def get_gi_value(self, ingredient: str) -> float:
        """
        Get GI value for an ingredient from database or finetuned model.
        
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
                    gi_value = float(gi_value)
                    # Apply adjustments for high GI values
                    if gi_value > 100:
                        gi_value *= 0.8  # Subtract 20%
                    elif gi_value > 90:
                        gi_value *= 0.9  # Subtract 10%
                    return gi_value
        
        # If not in database, use finetuned model
        try:
            # Prepare input text
            input_text = f"Ingredient: {ingredient}"
            
            # Tokenize and get prediction
            inputs = self.tokenizer(input_text, return_tensors="pt", truncation=True, max_length=512)
            inputs = {k: v.to(self.device) for k, v in inputs.items()}
            
            with torch.no_grad():
                outputs = self.model(**inputs)
                logits = outputs.logits
                probs = torch.nn.functional.softmax(logits, dim=-1)
                predicted_class = torch.argmax(probs, dim=-1).item()
                
                # Get the GI value for the predicted class
                gi_value = self.gi_ranges[predicted_class]
                
                # Apply adjustments for high GI values
                if gi_value > 100:
                    gi_value *= 0.8  # Subtract 20%
                elif gi_value > 90:
                    gi_value *= 0.9  # Subtract 10%
                    
                return gi_value
            
        except Exception as e:
            print(f"Error getting GI value for {ingredient}: {str(e)}")
            return 50.0  # Default to middle value if error

    def calculate_glycemic_load(self, ingredients: List[Dict[str, Any]]) -> float:
        """
        Calculate the total glycemic load of a recipe using batch processing.
        
        Args:
            ingredients (List[Dict[str, Any]]): List of ingredient dictionaries
            
        Returns:
            float: Total glycemic load
        """
        total_load = 0.0
        
        try:
            # Prepare batch prompt for all ingredients
            ingredients_list = [ing['ingredient'] for ing in ingredients]
            prompt = f"""You are a nutritional database assistant. For each ingredient listed below, provide its carbohydrate content in grams per 100g.
            Return ONLY a valid JSON object with the following exact format, where each value is a number between 0 and 100:
            
            {{
                "ingredient1": number,
                "ingredient2": number
            }}
            
            Ingredients to analyze:
            {', '.join(ingredients_list)}
            
            Important:
            1. Return ONLY the JSON object, no other text
            2. Use exact ingredient names as keys
            3. Use numbers only for values
            4. If uncertain about an ingredient, use 0 as the value"""
            
            # Single API call for all ingredients
            response = openai.chat.completions.create(
                model="gpt-4o-mini",
                messages=[
                    {"role": "system", "content": "You are a precise nutritional database that returns only valid JSON."},
                    {"role": "user", "content": prompt}
                ],
                temperature=0.1
            )
            
            # Parse the response to get carb contents
            response_text = response.choices[0].message.content.strip()
            print(f"Debug - Raw API Response: {response_text}")
            
            try:
                # Clean the response text to ensure it's valid JSON
                response_text = response_text.replace('\n', '').strip()
                # Remove any text before the first { and after the last }
                start_idx = response_text.find('{')
                end_idx = response_text.rfind('}') + 1
                if start_idx != -1 and end_idx != -1:
                    response_text = response_text[start_idx:end_idx]
                
                carb_contents = json.loads(response_text)
                print(f"Debug - Parsed Carb Contents: {carb_contents}")
                
                # Validate the response format
                if not isinstance(carb_contents, dict):
                    raise ValueError("Response is not a dictionary")
                
                # Ensure all ingredients have values
                for ing in ingredients_list:
                    if ing not in carb_contents:
                        carb_contents[ing] = 0.0
                    elif not isinstance(carb_contents[ing], (int, float)):
                        carb_contents[ing] = 0.0
                
            except (json.JSONDecodeError, ValueError) as e:
                print(f"Error parsing carb contents response: {str(e)}")
                print("Defaulting to 0 for all ingredients")
                carb_contents = {ing: 0.0 for ing in ingredients_list}
            
            # Process each ingredient
            for ingredient in ingredients:
                try:
                    # Get GI value
                    gi_value = self.get_gi_value(ingredient['ingredient'])
                    print(f"\nDebug - Ingredient: {ingredient['ingredient']}")
                    print(f"Debug - GI Value: {gi_value}")
                    
                    # Get carb content from batch response
                    carb_content = carb_contents.get(ingredient['ingredient'], 0.0)
                    print(f"Debug - Carb Content: {carb_content}")
                    
                    # Convert quantity to grams if needed
                    try:
                        # Handle fractions in quantity
                        quantity_str = str(ingredient['quantity']).strip()
                        if '/' in quantity_str:
                            num, denom = quantity_str.split('/')
                            quantity = float(num) / float(denom)
                        else:
                            # Handle special characters like ½
                            quantity_str = quantity_str.replace('½', '0.5').replace('¼', '0.25').replace('¾', '0.75')
                            quantity = float(quantity_str)
                    except (ValueError, TypeError):
                        print(f"Error converting quantity for {ingredient['ingredient']}, defaulting to 1")
                        quantity = 1.0
                    
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
                    elif unit in ['tbsp', 'tablespoon', 'tablespoons']:
                        quantity *= 15
                    elif unit in ['tsp', 'teaspoon', 'teaspoons']:
                        quantity *= 5
                    
                    print(f"Debug - Original Quantity: {ingredient['quantity']} {unit}")
                    print(f"Debug - Converted Quantity (g): {quantity}")
                    
                    # Calculate glycemic load for this ingredient
                    actual_carbs = (carb_content * quantity) / 100  # Convert percentage to actual grams
                    ingredient_load = (gi_value * actual_carbs) / 100
                    print(f"Debug - Actual Carbs (g): {actual_carbs}")
                    print(f"Debug - Ingredient Load: {ingredient_load}")
                    
                    total_load += ingredient_load
                    
                except Exception as e:
                    print(f"Error calculating load for {ingredient['ingredient']}: {str(e)}")
                    continue
            
            print(f"\nDebug - Total Glycemic Load: {total_load}")
            return total_load
            
        except Exception as e:
            print(f"Error in batch processing: {str(e)}")
            return 0.0

    def analyze_glycemic_load(self, glycemic_load: float) -> Dict[str, Any]:
        """
        Analyze the glycemic load and provide recommendations.
        
        Args:
            glycemic_load (float): Total glycemic load of the recipe
            
        Returns:
            Dict[str, Any]: Analysis results and recommendations
        """
        if glycemic_load < 10:
            category = "Low"
            recommendation = "This recipe has a low glycemic load and is suitable for most people, including those with diabetes."
        elif glycemic_load < 20:
            category = "Medium"
            recommendation = "This recipe has a moderate glycemic load. Consider pairing with protein and fiber-rich foods."
        else:
            category = "High"
            recommendation = "This recipe has a high glycemic load. Consider reducing portion size or balancing with low-GI foods."
        
        return {
            "glycemic_load": glycemic_load,
            "category": category,
            "recommendation": recommendation
        }

    def has_high_gi_ingredient(self, title: str) -> bool:
        """Check if recipe title contains high GI ingredients"""
        title_lower = title.lower()
        title_words = set(title_lower.split())
        
        # Check if any high GI ingredient is in the title words
        for ingredient in self.HIGH_GI_INGREDIENTS:
            if ingredient.lower() in title_words:
                return True
        return False

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
                print(f"\nInitial glycemic load for {recipe['title']}: {glycemic_load}")
                
                # Add weight for high GI ingredients in title
                if self.has_high_gi_ingredient(recipe['title']):
                    if glycemic_load <= 6:
                        glycemic_load += 40
                        print(f"Added 40 for high GI ingredients with low initial load in: {recipe['title']}")
                    elif glycemic_load <= 40:
                        glycemic_load += 20
                        print(f"Added 20 for high GI ingredients in: {recipe['title']}")
                    print(f"Glycemic load after high GI adjustment: {glycemic_load}")
                
                # Apply percentage adjustments based on final load
                original_load = glycemic_load
                if glycemic_load > 100:
                    glycemic_load = glycemic_load * 0.8  # Subtract 20%
                    print(f"Applied 20% reduction for load > 100: {original_load} -> {glycemic_load}")
                elif glycemic_load > 80:
                    glycemic_load = glycemic_load * 0.9  # Subtract 10%
                    print(f"Applied 10% reduction for load > 80: {original_load} -> {glycemic_load}")
                
                print(f"Final glycemic load after all adjustments: {glycemic_load}")
                
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
                        'gl_analysis': gl_analysis,
                        'has_high_gi_ingredient': self.has_high_gi_ingredient(recipe['title'])
                    }
            
            if not best_recipe:
                return {"error": "No valid recipes found for GI analysis"}
            
            return best_recipe
            
        except Exception as e:
            print(f"Error processing recipes: {str(e)}")
            return {"error": str(e)}
