import modal
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Dict, Optional, Any

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
    ingredients: List[Ingredient]  # Keep as List[Ingredient] to match curl output
    instructions: List[str]
    gl_analysis: Dict[str, Any]
    nutritional_info: Optional[Dict] = None

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

        # Convert the raw recipe data to RecipeResponse model
        recipe_response = RecipeResponse(
            title=best_recipe['title'],
            glycemic_load=best_recipe['glycemic_load'],
            ingredients=[
                Ingredient(
                    quantity=str(ing['quantity']),  # Ensure quantity is string
                    unit=ing['unit'],
                    ingredient=ing['ingredient']
                ) for ing in best_recipe['ingredients']
            ],
            instructions=best_recipe['instructions'],
            gl_analysis=best_recipe['gl_analysis']
        )
        
        return recipe_response.dict()
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
    def recommend_recipe(request: RecipeRequest):
        try:
            # Initialize agents
            recipe_agent, gi_agent = initialize_agents.local()
            
            # Process request
            result = process_recipe_request.local(
                request.query,
                recipe_agent,
                gi_agent
            )
            
            if "error" in result:
                raise HTTPException(status_code=400, detail=result["error"])
                
            return result
        except Exception as e:
            raise HTTPException(status_code=500, detail=str(e))
    
    return web_app