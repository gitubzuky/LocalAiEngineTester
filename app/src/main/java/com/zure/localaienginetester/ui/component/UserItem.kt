package com.zure.localaienginetester.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zure.localaienginetester.domain.entity.User
import com.zure.localaienginetester.ui.theme.LocalAIEngineTesterTheme

// [Example] 参考示例，正式开发时去除

/**
 * 用户列表项组件。可复用的 Composable 卡片。
 */
@Composable
fun UserItem(
    user: User,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(text = user.name, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = user.email, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Mock 数据助手，供 @Preview 使用 */
object UserMockData {
    val sampleUser = User(id = 1, name = "Alice", email = "alice@example.com")
}

@Preview(showBackground = true)
@Composable
fun UserItemPreview() {
    LocalAIEngineTesterTheme {
        UserItem(user = UserMockData.sampleUser)
    }
}
