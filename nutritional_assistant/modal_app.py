import modal
from fastapi import FastAPI
from pydantic import BaseModel
from typing import List, Dict, Optional

# Create Modal app
app = modal.App("nutritional-assistant")

# Define the image with dependencies and local files
image = modal.Image.debian_slim().pip_install(
    "fastapi[standard]",
    "uvicorn", 
    "python-dotenv",
    "openai",
    "torch",
    "transformers",
    "sentence-transformers", 
    "chromadb",
    "pandas",
    "pydantic"
).add_local_dir(
    "agents",  # Local agents directory
    "/root/agents"  # Path in the container
).add_local_dir(
    "data preprocessing/processed_data",
    "/root/data preprocessing/processed_data"
).add_local_dir(
    "vector_database/recipes_vectorstore", 
    "/root/vector_database/recipes_vectorstore"
)

# Request/Response models
class RecipeRequest(BaseModel):
    query: str

class Ingredient(BaseModel):
    quantity: str
    unit: str
    ingredient: str

class RecipeResponse(BaseModel):
    title: str
    glycemic_load: float
    ingredients: List[Ingredient]
    instructions: List[str]
    nutritional_info: Optional[Dict] = None

class ErrorResponse(BaseModel):
    detail: str

# Initialize agents
@app.function(
    image=image,
    secrets=[
        modal.Secret.from_name("openai-secret"),
        modal.Secret.from_name("hf-secret")
    ]
)
def initialize_agents():
    import sys
    sys.path.append("/root")  # Add the root directory to Python path
    
    from agents.recipe_agent import RecipeRecommendationAgent
    from agents.gi_agent_roberta_finetuned_2 import GIAnalysisAgentRoBERTaFinetuned2
    
    # Initialize agents with default paths
    recipe_agent = RecipeRecommendationAgent()
    gi_agent = GIAnalysisAgentRoBERTaFinetuned2()
    return recipe_agent, gi_agent

# Process recipe request
@app.function(
    image=image
)
def process_recipe_request(query: str, recipe_agent, gi_agent):
    try:
        # Get recipes
        recipe_results = recipe_agent.process(query)
        if 'error' in recipe_results:
            return {"error": recipe_results['error']}
        
        if not recipe_results.get('recipes'):
            return {"error": "No recipes found"}

        # Analyze recipes
        best_recipe = gi_agent.process(recipe_results['recipes'])
        if 'error' in best_recipe:
            return {"error": best_recipe['error']}

        return best_recipe
    except Exception as e:
        return {"error": str(e)}

# FastAPI app
@app.function(
    image=image,
    secrets=[
        modal.Secret.from_name("openai-secret"),
        modal.Secret.from_name("hf-secret")
    ]
)
@modal.asgi_app()
def fastapi_app():
    web_app = FastAPI()
    
    @web_app.post("/recommend_recipe")
    async def recommend_recipe(request: RecipeRequest):
        # Initialize agents
        recipe_agent, gi_agent = initialize_agents.local()
        
        # Process request
        result = process_recipe_request.local(
            request.query,
            recipe_agent,
            gi_agent
        )
        
        if "error" in result:
            return {"error": result["error"]}
            
        return result
    
    return web_app