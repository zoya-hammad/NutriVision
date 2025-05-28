import logging

logging.basicConfig(
    level=logging.INFO,  
    format='%(message)s' 
)

class BaseAgent:
    """
    An abstract superclass for Agents
    Used to log messages in a way that can identify each Agent
    """

    def __init__(self, name: str):
        self.name = name

    def log(self, message: str):
        """
        Log this as an info message, identifying the agent
        """
        message = f"[{self.name}] {message}"
        logging.info(message) 