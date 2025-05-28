import os
import json
import openai
import chromadb
import pandas as pd
from typing import List, Dict, Any, Tuple
from sentence_transformers import SentenceTransformer
from .base_agent import BaseAgent

class RecipeRecommendationAgent(BaseAgent):
    """
    Agent responsible for recommending recipes based on user queries and dietary guidelines.
    Returns 5 recipes with nutritional information for glycemic load analysis.
    """
    
    def __init__(self):
        """Initialize the recipe recommendation agent"""
        super().__init__(
            name="Recipe Recommendation Agent"
        )
        
        # Initialize components
        self.model = SentenceTransformer('sentence-transformers/all-MiniLM-L6-v2')
        self.db_path = os.path.join(os.path.dirname(os.path.dirname(__file__)), "vector_database", "recipes_vectorstore")
        self.client = None
        self.collection = None
        self.initialize_vector_db()
        
        self.guidelines = self._load_dietary_guidelines()
        self.food_data = self._load_food_data()
        
    def initialize_vector_db(self):
        """Initialize connection to the existing vector database"""
        try:
            print("Connecting to vector database...")
            self.client = chromadb.PersistentClient(path=self.db_path)
            self.collection = self.client.get_collection("recipes")
            print("Connected to vector database successfully")
        except Exception as e:
            print(f"Error connecting to vector database: {str(e)}")
            raise
        
    def _load_dietary_guidelines(self) -> str:
        """Load and clean dietary guidelines from file"""
        guidelines_path = os.path.join(os.path.dirname(os.path.dirname(__file__)), "data preprocessing", "processed_data", "core_dietary_guidelines.txt")
        try:
            with open(guidelines_path, 'r') as f:
                guidelines = f.read()
            print("Successfully loaded dietary guidelines")
            return guidelines.replace('*', '').strip()
        except FileNotFoundError:
            print(f"Dietary guidelines file not found at {guidelines_path}")
            return ""

    def _load_food_data(self) -> pd.DataFrame:
        """Load food nutritional data"""
        food_data_path = os.path.join(os.path.dirname(os.path.dirname(__file__)), "data preprocessing", "processed_data", "cleaned_diabetic_foods.csv")
        try:
            df = pd.read_csv(food_data_path)
            print(f"Successfully loaded food data with {len(df)} entries")
            return df
        except FileNotFoundError:
            print(f"Food data file not found at {food_data_path}")
            return pd.DataFrame()

    def vector(self, query):
        return self.model.encode([query])

    def find_similar_recipes(self, query: str, n_results: int = 5) -> Tuple[List[str], List[Dict[str, Any]]]:
        """
        Find similar recipes based on user query.
        
        Args:
            query (str): The user's query
            n_results (int): Number of results to return
            
        Returns:
            Tuple[List[str], List[Dict[str, Any]]]: A tuple containing the list of recipe documents and their metadata
        """
        try:
            print(f"Starting recipe search for query: '{query}'")
            
            # Get query vector and query the database
            results = self.collection.query(
                query_embeddings=self.vector(query).astype(float).tolist(),
                n_results=n_results
            )
            
            if not results or not results['documents']:
                print("No results found in vector database")
                return [], []
            
            documents = results['documents'][0]
            metadatas = results['metadatas'][0]
            
            return documents, metadatas
            
        except Exception as e:
            print(f"Error finding similar recipes: {str(e)}")
            return [], []

    def parse_recipe(self, doc: str, metadata: Dict[str, Any]) -> Dict[str, Any]:
        """Parse a recipe document into structured format"""
        try:
            # Parse the document text to extract recipe components
            recipe_text = doc.strip()
            lines = recipe_text.split('\n')
            
            # Initialize recipe components
            title = ""
            ingredients = []
            instructions = []
            ner = []
            
            # Parse each line
            for line in lines:
                line = line.strip()
                if not line:
                    continue
                
                if line.startswith('Title:'):
                    title = line[6:].strip()
                elif line.startswith('Ingredients:'):
                    ingredients_text = line[12:].strip()
                    ingredients = [ing.strip() for ing in ingredients_text.split(',')]
                elif line.startswith('Instructions:'):
                    instructions_text = line[13:].strip()
                    if instructions_text.startswith('[') and instructions_text.endswith(']'):
                        instructions_text = instructions_text[1:-1]
                        instructions = [inst.strip().strip("'") for inst in instructions_text.split("', '")]
                elif line.startswith('NER:'):
                    ner_text = line[4:].strip()
                    if ner_text.startswith('[') and ner_text.endswith(']'):
                        ner_text = ner_text[1:-1]
                        ner = [item.strip().strip("'") for item in ner_text.split("', '")]
            
            return {
                'title': title,
                'ingredients': ingredients,
                'instructions': instructions,
                'ner': ner,
                'raw_text': doc,
                'metadata': metadata
            }
            
        except Exception as e:
            print(f"Error parsing recipe: {str(e)}")
            return None

    def create_system_prompt(self) -> str:
        """Create system prompt with dietary guidelines"""
        return f"""You are a nutritional assistant that recommends recipes based on dietary guidelines.
        Here are the core dietary guidelines to follow:
        {self.guidelines}
        
        For each recipe, provide:
        1. Detailed ingredients list with quantities
        2. Clear step-by-step instructions
        3. Nutritional information (calories, protein, carbs, fat)
        4. Special dietary considerations
        5. Glycemic index information if available"""

    def create_user_prompt(self, user_query: str, recipes: List[Dict[str, Any]]) -> str:
        """Create user prompt with context from similar recipes"""
        context = "Here are some similar recipes for reference:\n\n"
        for i, recipe in enumerate(recipes, 1):
            context += f"Recipe {i}:\n"
            context += f"Title: {recipe['title']}\n"
            context += f"Ingredients: {', '.join(recipe['ingredients'])}\n"
            context += f"Instructions: {' '.join(recipe['instructions'])}\n\n"
        
        context += f"\nBased on the above context and the following request, please provide detailed nutritional information for each recipe:\n{user_query}"
        return context

    def process(self, input_data: str) -> Dict[str, Any]:
        """
        Process the user query and return 5 recipes with nutritional information.
        
        Args:
            input_data (str): User query for recipe recommendation
            
        Returns:
            Dict[str, Any]: Dictionary containing recipes and their nutritional information
        """
        print(f"Processing query: {input_data}")
        
        try:
            # Find similar recipes
            documents, metadatas = self.find_similar_recipes(input_data)
            
            if not documents:
                print("No recipes found for the query")
                return {"error": "No recipes found matching your query"}
            
            # Parse recipes
            recipes = []
            for doc, metadata in zip(documents, metadatas):
                recipe = self.parse_recipe(doc, metadata)
                if recipe:
                    recipes.append(recipe)
            
            if not recipes:
                print("No valid recipes found after parsing")
                return {"error": "No valid recipes found"}
            
            # Create messages for GPT
            messages = [
                {"role": "system", "content": self.create_system_prompt()},
                {"role": "user", "content": self.create_user_prompt(input_data, recipes)}
            ]
            
            # Get recommendation from GPT
            response = openai.chat.completions.create(
                model="gpt-4o-mini",
                messages=messages,
                temperature=0.3,
                max_tokens=2000
            )
            
            nutritional_info = response.choices[0].message.content
            print("Recipe recommendations generated successfully")
            
            return {
                "recipes": recipes,
                "nutritional_info": nutritional_info,
                "food_data": self.food_data.to_dict() if not self.food_data.empty else {}
            }
            
        except Exception as e:
            print(f"Error processing query: {str(e)}")
            return {"error": str(e)} 