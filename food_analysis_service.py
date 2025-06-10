from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import Optional
import openai
import json
import modal

# Create Modal app
app = modal.App("food-analysis-service")

# Create Modal image
image = modal.Image.debian_slim().pip_install(
    "fastapi",
    "openai",
    "pydantic"
)

# Create Modal secret
app.secret = modal.Secret.from_name("openai-secret")

class FoodAnalysisRequest(BaseModel):
    food_name: str
    description: str
    user_age: Optional[str] = None
    dietary_restrictions: Optional[str] = None
    allergies: Optional[str] = None

class FoodAnalysisResponse(BaseModel):
    calories: int
    glycemic_load: str
    advice: str

# Create FastAPI app
web_app = FastAPI()

@web_app.post("/analyze-food", response_model=FoodAnalysisResponse)
async def analyze_food(request: FoodAnalysisRequest):
    try:
        prompt = f"""
        Analyze the following food item and provide nutritional information:
        Food: {request.food_name}
        Description: {request.description}
        
        User Context:
        Age: {request.user_age or "Not specified"}
        Dietary Restrictions: {request.dietary_restrictions or "None"}
        Allergies: {request.allergies or "None"}
        
        Please provide:
        1. Estimated calories
        2. Glycemic load (low/medium/high)
        3. Personalized advice based on user's context
        
        Format the response as JSON:
        {{
            "calories": number,
            "glycemic_load": "low/medium/high",
            "advice": "personalized advice"
        }}
        """

        response = openai.chat.completions.create(
            model="gpt-3.5-turbo",
            messages=[
                {"role": "user", "content": prompt}
            ],
            temperature=0.7
        )

        # Parse the response content as JSON
        content = response.choices[0].message.content.strip()
        try:
            analysis = json.loads(content)
        except json.JSONDecodeError as e:
            raise HTTPException(
                status_code=500,
                detail=f"Failed to parse OpenAI response: {str(e)}. Response content: {content}"
            )

        # Validate required fields
        required_fields = ["calories", "glycemic_load", "advice"]
        missing_fields = [field for field in required_fields if field not in analysis]
        if missing_fields:
            raise HTTPException(
                status_code=500,
                detail=f"OpenAI response missing required fields: {missing_fields}. Response: {content}"
            )

        return FoodAnalysisResponse(
            calories=analysis["calories"],
            glycemic_load=analysis["glycemic_load"],
            advice=analysis["advice"]
        )

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Error analyzing food: {str(e)}"
        )

# Create Modal function
@app.function(image=image, secrets=[app.secret])
@modal.asgi_app()
def fastapi_app():
    return web_app 