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
        super().__init__(
            name="GI Analysis Agent (Finetuned 2)",
            color=self.BLUE
        )
        
        # Load food database
        self.food_data = self._load_food_data()
        load_dotenv()
        
        # Login to Hugging Face
        hf_token = os.getenv('HF_TOKEN')
        login(token=hf_token)
        
        # Load tokenizer and model
        self.tokenizer = AutoTokenizer.from_pretrained("roberta-base")
        self.model = AutoModelForSequenceClassification.from_pretrained("zoya-hammadk/nutrivision-roberta-classification")
        
        # Move model to GPU if available
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        self.model = self.model.to(self.device)
        self.model.eval()
        
        # Define GI ranges for each class
        self.gi_ranges = {
            0: 5.0,   # 0-10
            1: 15.0,  # 11-20
            2: 25.0,  # 21-30
            3: 35.0,  # 31-40
            4: 45.0,  # 41-50
            5: 55.0,  # 51-60
            6: 65.0,  # 61-70
            7: 75.0,  # 71-80
            8: 85.0,  # 81-90
            9: 95.0   # 91-100
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
                    return float(gi_value)
        
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
                return self.gi_ranges[predicted_class]
            
        except Exception as e:
            print(f"Error getting GI value for {ingredient}: {str(e)}")
            return 50.0  # Default to middle value if error

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
                print(f"\nDebug - Ingredient: {ingredient['ingredient']}")
                print(f"Debug - GI Value: {gi_value}")
                
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
                
                print(f"Debug - Carb Content: {carb_content}")
                
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
                elif unit in ['tbsp', 'tablespoon', 'tablespoons']:
                    quantity *= 15
                elif unit in ['tsp', 'teaspoon', 'teaspoons']:
                    quantity *= 5
                
                print(f"Debug - Original Quantity: {ingredient['quantity']} {unit}")
                print(f"Debug - Converted Quantity (g): {quantity}")
                
                # Calculate glycemic load for this ingredient
                # Formula: GL = (GI × grams of carbohydrate) / 100
                # We need to calculate the actual grams of carbs in the quantity
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
