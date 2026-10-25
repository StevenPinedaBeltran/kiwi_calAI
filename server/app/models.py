from pydantic import BaseModel, Field

COLOR_PATTERN = r"^#[0-9a-fA-F]{6}$"


class EventIn(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    start: str
    end: str | None = None
    all_day: bool = False
    location: str | None = Field(default=None, max_length=300)
    notes: str | None = Field(default=None, max_length=4000)
    color: str | None = Field(default=None, pattern=COLOR_PATTERN)


class FeedIn(BaseModel):
    name: str = Field(min_length=1, max_length=80)
    url: str = Field(min_length=8, max_length=2000)
    color: str = Field(default="#3B6EA8", pattern=COLOR_PATTERN)


class AlbumIn(BaseModel):
    url: str = Field(min_length=10, max_length=500)
