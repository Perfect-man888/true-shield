from __future__ import annotations

import uuid

import pytest
from httpx import AsyncClient

TEST_PASSWORD = "FamilyInvitationTest_123!"

pytestmark = pytest.mark.asyncio


async def register_and_login(
    client: AsyncClient,
    *,
    name: str,
) -> tuple[str, dict[str, str]]:
    """注册并登录测试用户。"""

    email = (
        f"{name.lower()}-"
        f"{uuid.uuid4().hex}@example.com"
    )

    register_response = await client.post(
        "/api/v1/auth/register",
        json={
            "email": email,
            "phone": None,
            "display_name": name,
            "password": TEST_PASSWORD,
        },
    )

    assert register_response.status_code in {
        200,
        201,
    }, register_response.text

    login_response = await client.post(
        "/api/v1/auth/login",
        data={
            "username": email,
            "password": TEST_PASSWORD,
        },
    )

    assert login_response.status_code == 200, (
        login_response.text
    )

    token = login_response.json()[
        "access_token"
    ]

    return email, {
        "Authorization": f"Bearer {token}",
    }


async def create_family(
    client: AsyncClient,
    *,
    headers: dict[str, str],
) -> dict:
    """创建测试家庭。"""

    response = await client.post(
        "/api/v1/families",
        headers=headers,
        json={
            "name": "邀请测试家庭",
        },
    )

    assert response.status_code == 201, (
        response.text
    )

    return response.json()


async def send_invitation(
    client: AsyncClient,
    *,
    family_id: str,
    headers: dict[str, str],
    invitee_email: str,
) -> dict:
    """发送家庭邀请。"""

    response = await client.post(
        (
            f"/api/v1/families/{family_id}"
            "/invitations"
        ),
        headers=headers,
        json={
            "invitee_email": invitee_email,
            "role": "member",
        },
    )

    assert response.status_code == 201, (
        response.text
    )

    return response.json()


async def test_owner_can_send_invitation(
    client: AsyncClient,
) -> None:
    """家庭所有者可以发送邀请。"""

    _, owner_headers = await register_and_login(
        client,
        name="InvitationOwner",
    )

    invitee_email, _ = await register_and_login(
        client,
        name="InvitationTarget",
    )

    family = await create_family(
        client,
        headers=owner_headers,
    )

    invitation = await send_invitation(
        client,
        family_id=family["id"],
        headers=owner_headers,
        invitee_email=invitee_email,
    )

    assert invitation["family_id"] == family["id"]
    assert invitation["invitee_email"] == invitee_email
    assert invitation["role"] == "member"
    assert invitation["status"] == "pending"


async def test_invitee_can_list_received_invitations(
    client: AsyncClient,
) -> None:
    """被邀请人可以查询收到的邀请。"""

    _, owner_headers = await register_and_login(
        client,
        name="InvitationListOwner",
    )

    invitee_email, invitee_headers = (
        await register_and_login(
            client,
            name="InvitationListTarget",
        )
    )

    family = await create_family(
        client,
        headers=owner_headers,
    )

    invitation = await send_invitation(
        client,
        family_id=family["id"],
        headers=owner_headers,
        invitee_email=invitee_email,
    )

    response = await client.get(
        "/api/v1/families/invitations/received",
        headers=invitee_headers,
    )

    assert response.status_code == 200, (
        response.text
    )

    body = response.json()

    assert body["total"] == 1
    assert body["items"][0]["id"] == invitation["id"]
    assert body["items"][0]["family_name"] == (
        "邀请测试家庭"
    )


async def test_invitee_can_accept_invitation(
    client: AsyncClient,
) -> None:
    """接受邀请后应成为家庭成员。"""

    _, owner_headers = await register_and_login(
        client,
        name="AcceptOwner",
    )

    invitee_email, invitee_headers = (
        await register_and_login(
            client,
            name="AcceptTarget",
        )
    )

    family = await create_family(
        client,
        headers=owner_headers,
    )

    invitation = await send_invitation(
        client,
        family_id=family["id"],
        headers=owner_headers,
        invitee_email=invitee_email,
    )

    response = await client.post(
        (
            "/api/v1/families/invitations/"
            f"{invitation['id']}/accept"
        ),
        headers=invitee_headers,
    )

    assert response.status_code == 200, (
        response.text
    )

    body = response.json()

    assert body["family_id"] == family["id"]
    assert body["role"] == "member"
    assert body["status"] == "accepted"

    families_response = await client.get(
        "/api/v1/families",
        headers=invitee_headers,
    )

    assert families_response.status_code == 200

    families_body = families_response.json()

    assert families_body["total"] == 1
    assert families_body["items"][0]["id"] == (
        family["id"]
    )


async def test_invitee_can_decline_invitation(
    client: AsyncClient,
) -> None:
    """被邀请人可以拒绝邀请。"""

    _, owner_headers = await register_and_login(
        client,
        name="DeclineOwner",
    )

    invitee_email, invitee_headers = (
        await register_and_login(
            client,
            name="DeclineTarget",
        )
    )

    family = await create_family(
        client,
        headers=owner_headers,
    )

    invitation = await send_invitation(
        client,
        family_id=family["id"],
        headers=owner_headers,
        invitee_email=invitee_email,
    )

    response = await client.post(
        (
            "/api/v1/families/invitations/"
            f"{invitation['id']}/decline"
        ),
        headers=invitee_headers,
    )

    assert response.status_code == 200, (
        response.text
    )

    assert response.json()["status"] == "declined"


async def test_other_user_cannot_accept_invitation(
    client: AsyncClient,
) -> None:
    """其他用户不能接受不属于自己的邀请。"""

    _, owner_headers = await register_and_login(
        client,
        name="ProtectedInviteOwner",
    )

    invitee_email, _ = await register_and_login(
        client,
        name="ProtectedInviteTarget",
    )

    _, other_headers = await register_and_login(
        client,
        name="OtherInviteUser",
    )

    family = await create_family(
        client,
        headers=owner_headers,
    )

    invitation = await send_invitation(
        client,
        family_id=family["id"],
        headers=owner_headers,
        invitee_email=invitee_email,
    )

    response = await client.post(
        (
            "/api/v1/families/invitations/"
            f"{invitation['id']}/accept"
        ),
        headers=other_headers,
    )

    assert response.status_code == 404


async def test_duplicate_pending_invitation_is_rejected(
    client: AsyncClient,
) -> None:
    """不能重复发送有效的待处理邀请。"""

    _, owner_headers = await register_and_login(
        client,
        name="DuplicateInviteOwner",
    )

    invitee_email, _ = await register_and_login(
        client,
        name="DuplicateInviteTarget",
    )

    family = await create_family(
        client,
        headers=owner_headers,
    )

    await send_invitation(
        client,
        family_id=family["id"],
        headers=owner_headers,
        invitee_email=invitee_email,
    )

    response = await client.post(
        (
            f"/api/v1/families/{family['id']}"
            "/invitations"
        ),
        headers=owner_headers,
        json={
            "invitee_email": invitee_email,
            "role": "member",
        },
    )

    assert response.status_code == 409

    assert response.json()["detail"] == (
        "该用户已经存在待处理邀请。"
    )